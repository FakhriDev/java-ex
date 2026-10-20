import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Консольное приложение для очереди печати. Реализованы назначенные строки
 * для N=5: A1, B6 и V2.
 */
public class Main {
    private static final Path STATE_FILE = Paths.get("print_state.dat");
    private static final int INITIAL_PAPER = 14;

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            boolean savedFileExists = Files.exists(STATE_FILE);
            PrintState state = loadState();

            if (state == null && savedFileExists) {
                if (!askYesNo(scanner, "Начать новую цепочку? (д/н): ")) {
                    System.out.println("Программа завершена.");
                    return;
                }
            }

            if (state == null) {
                state = createNewState(scanner);
                if (state == null) {
                    System.out.println("Ввод завершён. Программа закрыта.");
                    return;
                }
                System.out.println("Создана новая цепочка для N=5. Вариант A"
                        + state.stageA + ". Начальный запас: " + INITIAL_PAPER + " листов.");
            } else {
                System.out.println("Сохранённая цепочка восстановлена для N=" + state.n + ".");
                System.out.println("Последний завершённый этап: " + state.lastCompleted + ".");
            }

            runMenu(scanner, state);
        }
    }

    /**
     * Загружает сериализованное состояние. null означает, что начать нужно
     * новую цепочку или что пользователь должен подтвердить новый запуск.
     */
    private static PrintState loadState() {
        if (!Files.exists(STATE_FILE)) {
            return null;
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new FileInputStream(STATE_FILE.toFile()))) {
            Object stored = input.readObject();
            if (!(stored instanceof PrintState)) {
                throw new InvalidObjectException("Файл содержит объект неизвестного типа.");
            }
            PrintState state = (PrintState) stored;
            state.validate();
            return state;
        } catch (IOException | ClassNotFoundException | RuntimeException exception) {
            System.out.println("Не удалось прочитать сохранение: " + exception.getMessage());
            return null;
        }
    }

    /**
     * Запрашивает N один раз для новой цепочки. Эта версия задания рассчитана
     * на выданный номер 5, поэтому для других N здесь предлагается ввести 5.
     */
    private static PrintState createNewState(Scanner scanner) {
        while (true) {
            Integer n = readInteger(scanner, "Введите начальный номер N (1..36): ");
            if (n == null) {
                return null;
            }
            if (n < 1 || n > 36) {
                System.out.println("N должен быть в диапазоне от 1 до 36.");
                continue;
            }
            if (n != 5) {
                System.out.println("Эта реализация подготовлена для N=5. Введите 5.");
                continue;
            }
            return new PrintState(n);
        }
    }

    private static void runMenu(Scanner scanner, PrintState state) {
        boolean running = true;
        while (running) {
            printMenu();
            Integer choice = readInteger(scanner, "Ваш выбор: ");
            if (choice == null) {
                // При завершении ввода тоже пытаемся сохранить текущую работу.
                saveState(state);
                return;
            }

            switch (choice) {
                case 1:
                    completeStage1(state);
                    break;
                case 2:
                    completeStage2(state);
                    break;
                case 3:
                    completeStage3(state);
                    break;
                case 4:
                    showQueue(state);
                    break;
                case 5:
                    if (saveState(state)) {
                        running = false;
                        System.out.println("Состояние сохранено. До свидания.");
                    }
                    // Если запись не удалась, пользователь остаётся в меню.
                    break;
                default:
                    System.out.println("Выберите пункт меню от 1 до 5.");
            }
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("Меню:");
        System.out.println("1. Выполнить задание 1 и назначить вариант задания 2");
        System.out.println("2. Выполнить задание 2 и назначить вариант задания 3");
        System.out.println("3. Выполнить задание 3 и вывести отчёт");
        System.out.println("4. Показать очередь и остаток бумаги");
        System.out.println("5. Сохранить состояние и выйти");
    }

    /**
     * Этап 1, строка A1: priority >= 3 и потребность не более 8 листов.
     */
    private static void completeStage1(PrintState state) {
        if (state.lastCompleted >= 1) {
            System.out.println("Этап 1 уже завершён; очередь не изменена.");
            return;
        }
        if (state.stageA != 1) {
            System.out.println("Для этой версии реализована только строка A1.");
            return;
        }

        Predicate<PrintJob> condition = job ->
                job.getPriority() >= 3 && job.getRequiredSheets() <= 8;

        List<PrintJob> selected = createSourceJobs().stream()
                .filter(condition)
                .sorted(Comparator.comparingInt(PrintJob::getId))
                .collect(Collectors.toList());

        // Добавляем в очередь только задания, прошедшие условие A1.
        selected.forEach(state.queue::add);

        List<Integer> ids = selected.stream()
                .map(PrintJob::getId)
                .sorted()
                .collect(Collectors.toList());
        int totalSheets = selected.stream()
                .mapToInt(PrintJob::getRequiredSheets)
                .sum();

        state.lastCompleted = 1;
        System.out.println("Этап 1 завершён. Добавлены ID: " + ids);
        System.out.println("Суммарная потребность: " + totalSheets + " листов.");

        try {
            // Номер Б вычисляется по реальным ID и сумме, а не задаётся вручную.
            state.stageB = StageAccess.second(state.n, ids, totalSheets);
            System.out.println("Назначен вариант Б" + state.stageB + ".");
        } catch (IllegalStateException exception) {
            state.stageB = 0;
            System.out.println("StageAccess отклонил результат этапа 1: "
                    + exception.getMessage());
        }
    }

    /**
     * Этап 2. Для этих исходных данных назначен B6: владельцы с меньшим
     * числом прежних печатей идут первыми; затем priority по убыванию и ID.
     */
    private static void completeStage2(PrintState state) {
        if (state.lastCompleted >= 2) {
            System.out.println("Этап 2 уже завершён; очередь не изменена.");
            return;
        }
        if (state.lastCompleted < 1 || state.stageB == 0) {
            System.out.println("Сначала успешно завершите этап 1.");
            return;
        }
        if (state.stageB != 6) {
            System.out.println("Для вычисленного варианта Б" + state.stageB
                    + " в этой версии нет строки реализации.");
            return;
        }

        Comparator<PrintJob> order = stageB6Order(state.queue);
        Predicate<PrintJob> condition = job -> true;
        Consumer<PrintJob> action = PrintJob::markPrinted;
        List<PrintJob> completed = state.queue.process(condition, order, action);

        state.lastCompleted = 2;
        List<Integer> ids = completed.stream()
                .map(PrintJob::getId)
                .collect(Collectors.toList());
        System.out.println("Этап 2 завершён. Выполнены ID: " + ids);
        System.out.println("Остаток бумаги: " + state.queue.getPaper() + " лист.");

        try {
            // StageAccess получает только задания, напечатанные этим вызовом.
            state.stageV = StageAccess.third(state.n, ids, state.queue.getPaper());
            System.out.println("Назначен вариант В" + state.stageV + ".");
        } catch (IllegalStateException exception) {
            state.stageV = 0;
            System.out.println("StageAccess отклонил результат этапа 2: "
                    + exception.getMessage());
        }
    }

    /**
     * Этап 3. Назначенная строка V2 добавляет 5 листов и повторно применяет B6.
     */
    private static void completeStage3(PrintState state) {
        if (state.lastCompleted >= 3) {
            System.out.println("Этап 3 уже завершён; очередь не изменена.");
            return;
        }
        if (state.lastCompleted < 2 || state.stageV == 0) {
            System.out.println("Сначала успешно завершите этап 2.");
            return;
        }
        if (state.stageB != 6 || state.stageV != 2) {
            System.out.println("Для этой версии реализована только связка Б6 и В2.");
            return;
        }

        // V2: пополнить запас на 5 листов, после чего ровно один раз вызвать process().
        if (!state.queue.restock(5)) {
            System.out.println("Не удалось пополнить запас; очередь не изменена.");
            return;
        }

        List<PrintJob> completed = state.queue.process(
                job -> true,
                stageB6Order(state.queue),
                PrintJob::markPrinted);
        state.lastCompleted = 3;

        List<Integer> ids = completed.stream()
                .map(PrintJob::getId)
                .collect(Collectors.toList());
        System.out.println("Этап 3 завершён. Новые выполненные ID: " + ids);
        System.out.println("Остаток бумаги: " + state.queue.getPaper() + " лист.");

        // Отчёт V2: владельцы, у которых одновременно есть печатные и ожидающие задания.
        Map<Integer, List<PrintJob>> jobsByOwner = state.queue.getAnalyticsStream()
                .collect(Collectors.groupingBy(PrintJob::getOwnerId));
        List<Integer> ownersWithBothStatuses = jobsByOwner.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(PrintJob::isPrinted)
                        && entry.getValue().stream().anyMatch(job -> !job.isPrinted()))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        System.out.println("Отчёт В2 — владельцы с напечатанными и ожидающими заданиями: "
                + ownersWithBothStatuses);
    }

    /**
     * Создаёт компаратор B6. Число прежних печатей фиксируется до process().
     */
    private static Comparator<PrintJob> stageB6Order(PrintQueue<PrintJob> queue) {
        Map<Integer, Long> printedAtCallStart = queue.getAnalyticsStream()
                .filter(PrintJob::isPrinted)
                .collect(Collectors.groupingBy(PrintJob::getOwnerId, Collectors.counting()));

        return Comparator
                .comparingLong((PrintJob job) ->
                        printedAtCallStart.getOrDefault(job.getOwnerId(), 0L))
                .thenComparing(Comparator.comparingInt(PrintJob::getPriority).reversed())
                .thenComparingInt(PrintJob::getId);
    }

    private static List<PrintJob> createSourceJobs() {
        return Arrays.asList(
                new SingleSidedJob(101, 11, 5, 1, 5),
                new DoubleSidedJob(102, 11, 5, 2, 4),
                new SingleSidedJob(103, 12, 4, 2, 5),
                new DoubleSidedJob(104, 12, 7, 1, 3),
                new SingleSidedJob(105, 13, 3, 2, 2),
                new DoubleSidedJob(106, 13, 9, 1, 4),
                new DoubleSidedJob(107, 14, 2, 3, 1),
                new SingleSidedJob(108, 14, 6, 1, 3),
                new DoubleSidedJob(109, 12, 1, 2, 4),
                new SingleSidedJob(110, 15, 8, 1, 2)
        );
    }

    private static void showQueue(PrintState state) {
        System.out.println("Очередь:");
        if (state.queue.getAnalyticsStream().count() == 0) {
            System.out.println("(пока пуста)");
        } else {
            state.queue.getAnalyticsStream()
                    .sorted(Comparator.comparingInt(PrintJob::getId))
                    .map(PrintJob::getDetails)
                    .forEach(System.out::println);
        }
        System.out.println("Остаток бумаги: " + state.queue.getPaper() + " лист.");
    }

    private static Integer readInteger(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                return null;
            }
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException exception) {
                System.out.println("Введите целое число.");
            }
        }
    }

    private static boolean askYesNo(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                return false;
            }
            String answer = scanner.nextLine().trim().toLowerCase();
            if (answer.equals("д") || answer.equals("да") || answer.equals("y")
                    || answer.equals("yes")) {
                return true;
            }
            if (answer.equals("н") || answer.equals("нет") || answer.equals("n")
                    || answer.equals("no")) {
                return false;
            }
            System.out.println("Ответьте «д» или «н».");
        }
    }

    /**
     * Сохраняет полное состояние. false оставляет приложение в меню.
     */
    private static boolean saveState(PrintState state) {
        try (ObjectOutputStream output = new ObjectOutputStream(
                new FileOutputStream(STATE_FILE.toFile()))) {
            output.writeObject(state);
            return true;
        } catch (IOException exception) {
            System.out.println("Не удалось сохранить состояние: " + exception.getMessage());
            System.out.println("Текущие данные остаются в памяти; вернитесь в меню и повторите выход.");
            return false;
        }
    }

    /**
     * Состояние включает очередь, бумагу, N, назначенные варианты и последний этап.
     */
    private static final class PrintState implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int n;
        private final int stageA;
        private int stageB;
        private int stageV;
        private int lastCompleted;
        private final PrintQueue<PrintJob> queue;

        private PrintState(int n) {
            this.n = n;
            this.stageA = StageAccess.initial(n);
            this.stageB = 0;
            this.stageV = 0;
            this.lastCompleted = 0;
            this.queue = new PrintQueue<>(INITIAL_PAPER);
        }

        private void validate() throws InvalidObjectException {
            if (n != 5 || stageA != StageAccess.initial(n)
                    || stageB < 0 || stageB > 6
                    || stageV < 0 || stageV > 6
                    || lastCompleted < 0 || lastCompleted > 3
                    || queue == null) {
                throw new InvalidObjectException("Сохранённое состояние не соответствует заданию N=5.");
            }
        }
    }
}

final class StageAccess {
    private static final int[] S = {5, 3, 2, 4, 2, 1};
    private static final int[] A = {
            0xcb89387d, 0x4626b7fe, 0xacac2431, 0x4997263f, 0x6b20cc2b,
            0x209a21ae
    };
    private static final int[] B = {
            0x6128024d, 0x38a00d09, 0x38a2c749, 0x2ad3a538, 0x38a00d09,
            0x38a00d09, 0x612876ab, 0x612ba548, 0xe7a1669a, 0x612876ab,
            0xc4f9d0bb, 0x612876ab, 0xf3601d34, 0x013dc91f, 0xf3601d34,
            0x4471d939, 0x38a2c749, 0x2ad3a538, 0x6128024d, 0x38a00d09,
            0x38a16a29, 0x04b3adf2, 0x38a00d09, 0x38a00d09, 0x38a00d09,
            0x38a00d09, 0x6128024d, 0x38a00d09, 0x38a16a29, 0x04b3adf2,
            0x013dc91f, 0x4471d939, 0x4471d939, 0x013e3d80, 0x0d1d0f0f,
            0x4471d939
    };
    static int initial(int n) {
        if (n < 1 || n > 36)
            throw new IllegalArgumentException("N: 1..36");
        return (n - 1) / 6 + 1;
    }
    static int second(int n, java.util.List<Integer> ids, int sheets) {
        int a = initial(n);
        return check(n, ids, sheets, A[a - 1], 0);
    }
    static int third(int n, java.util.List<Integer> ids, int paper) {
        int a = initial(n);
        int b = 1 + Math.floorMod((long) A[a - 1] + (n - 1) % 6, 6);
        return check(n, ids, paper, B[n - 1], b);
    }
    private static int check(int n, java.util.List<Integer> ids,
                             int value, int expected, int previous) {
        if (ids == null) throw new IllegalArgumentException("ids: null");
        int actual = (ids + "|" + value).hashCode();
        if (actual != expected)
            throw new IllegalStateException("Check stage result");
        long key = previous == 0 ? actual
                : (actual >>> 6) + 0L * previous
                + (1 - 1L) * ((n - 1) % 6) + S[initial(n) - 1];
        return 1 + Math.floorMod(key + (n - 1) % 6, 6);
    }
}

/**
 * Общий контракт для печатных заданий, которые потребляют бумагу.
 */
interface PaperDemand {
    /**
     * Возвращает количество листов, необходимое для полного выполнения.
     */
    int getRequiredSheets();
}

/**
 * Общая часть любого задания на печать.
 * Параметры задания неизменяемы, а статус печати меняется только отдельно.
 */
abstract class PrintJob implements PaperDemand, Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final int ownerId;
    private final int pages;
    private final int copies;
    private final int priority;
    private boolean printed;

    protected PrintJob(int id, int ownerId, int pages, int copies, int priority) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID задания должен быть положительным.");
        }
        if (ownerId <= 0) {
            throw new IllegalArgumentException("ID владельца должен быть положительным.");
        }
        if (pages < 1 || pages > 100) {
            throw new IllegalArgumentException("Число страниц должно быть от 1 до 100.");
        }
        if (copies < 1 || copies > 100) {
            throw new IllegalArgumentException("Число копий должно быть от 1 до 100.");
        }
        if (priority < 1 || priority > 5) {
            throw new IllegalArgumentException("Приоритет должен быть от 1 до 5.");
        }

        this.id = id;
        this.ownerId = ownerId;
        this.pages = pages;
        this.copies = copies;
        this.priority = priority;
        this.printed = false;
    }

    public int getId() {
        return id;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public int getPages() {
        return pages;
    }

    public int getCopies() {
        return copies;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isPrinted() {
        return printed;
    }

    /**
     * Отмечает задание напечатанным после успешного списания бумаги.
     */
    public void markPrinted() {
        printed = true;
    }

    /**
     * Возвращает описание конкретного способа печати.
     */
    public abstract String getDetails();

    protected String commonDetails() {
        return "id=" + id
                + ", ownerId=" + ownerId
                + ", pages=" + pages
                + ", copies=" + copies
                + ", priority=" + priority
                + ", requiredSheets=" + getRequiredSheets()
                + ", status=" + (printed ? "напечатано" : "ожидает");
    }
}

/**
 * Односторонняя печать: каждая страница требует отдельный лист.
 */
final class SingleSidedJob extends PrintJob {
    private static final long serialVersionUID = 1L;

    public SingleSidedJob(int id, int ownerId, int pages, int copies, int priority) {
        super(id, ownerId, pages, copies, priority);
    }

    @Override
    public int getRequiredSheets() {
        return getPages() * getCopies();
    }

    @Override
    public String getDetails() {
        return "Одностороннее задание: " + commonDetails();
    }
}

/**
 * Двусторонняя печать: каждый экземпляр начинается на новом листе.
 */
final class DoubleSidedJob extends PrintJob {
    private static final long serialVersionUID = 1L;

    public DoubleSidedJob(int id, int ownerId, int pages, int copies, int priority) {
        super(id, ownerId, pages, copies, priority);
    }

    @Override
    public int getRequiredSheets() {
        // Для каждого экземпляра округляем число листов вверх отдельно.
        return ((getPages() + 1) / 2) * getCopies();
    }

    @Override
    public String getDetails() {
        return "Двустороннее задание: " + commonDetails();
    }
}

/**
 * Обобщённая очередь заданий и доступный запас бумаги.
 */
class PrintQueue<T extends PrintJob> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_PAPER = 1_000_000;

    private List<T> jobs;
    private int paper;

    public PrintQueue(int paper) {
        if (paper < 0 || paper > MAX_PAPER) {
            throw new IllegalArgumentException("Запас бумаги должен быть от 0 до 1 000 000.");
        }
        this.jobs = new ArrayList<>();
        this.paper = paper;
    }

    /**
     * Добавляет задание, если оно не null, ещё не напечатано и его ID уникален.
     */
    public boolean add(T job) {
        if (job == null || job.isPrinted()) {
            return false;
        }
        boolean idAlreadyExists = jobs.stream().anyMatch(existing -> existing.getId() == job.getId());
        if (idAlreadyExists) {
            return false;
        }
        return jobs.add(job);
    }

    public int getPaper() {
        return paper;
    }

    /**
     * Пополняет запас только если итог не превышает установленный максимум.
     */
    public boolean restock(int amount) {
        if (amount <= 0 || amount > MAX_PAPER - paper) {
            return false;
        }
        paper += amount;
        return true;
    }

    /**
     * Возвращает новый поток всех заданий, в том числе уже напечатанных.
     */
    public Stream<T> getAnalyticsStream() {
        return jobs.stream();
    }

    /**
     * Обрабатывает ожидающие задания в порядке Comparator.
     * Пропущенное задание не мешает рассмотреть следующие.
     */
    public List<T> process(Predicate<T> condition,
                           Comparator<T> order,
                           Consumer<T> action) {
        if (condition == null || order == null || action == null) {
            throw new IllegalArgumentException("Условие, порядок и действие не должны быть null.");
        }

        List<T> pendingInOrder = jobs.stream()
                .filter(job -> !job.isPrinted())
                .sorted(order)
                .collect(Collectors.toList());
        List<T> completed = new ArrayList<>();

        for (T job : pendingInOrder) {
            // Условие проверяется прямо перед рассмотрением задания.
            if (condition.test(job) && job.getRequiredSheets() <= paper) {
                paper -= job.getRequiredSheets();
                action.accept(job);
                completed.add(job);
            }
        }
        return completed;
    }
}
