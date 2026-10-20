import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/** Очередь печати для N=5: этапы A1, B6 и V2. */
public class Main {
    private static final Path FILE = Paths.get("print_state.dat");
    private static final int INITIAL_PAPER = 14;

    public static void main(String[] args) {
        try (Scanner in = new Scanner(System.in)) {
            boolean hadFile = Files.exists(FILE);
            PrintState state = load();
            if (state == null && hadFile
                    && !yesNo(in, "Ошибка сохранения. Начать заново? (д/н): ")) return;

            if (state == null) {
                state = newState(in);
                if (state == null) return;
            } else {
                System.out.println("Состояние восстановлено. Завершён этап: " + state.lastStage);
            }
            menu(in, state);
        }
    }

    private static PrintState load() {
        if (!Files.exists(FILE)) return null;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(FILE.toFile()))) {
            Object value = in.readObject();
            if (!(value instanceof PrintState))
                throw new InvalidObjectException("Неверный формат файла.");
            PrintState state = (PrintState) value;
            state.validate();
            return state;
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            System.out.println("Не удалось прочитать сохранение: " + e.getMessage());
            return null;
        }
    }

    private static PrintState newState(Scanner in) {
        while (true) {
            Integer n = readInt(in, "Введите N (1..36): ");
            if (n == null) return null;
            if (n != 5) {
                System.out.println("Этот код подготовлен для N=5.");
                continue;
            }
            return new PrintState(n);
        }
    }

    private static void menu(Scanner in, PrintState s) {
        while (true) {
            System.out.println("\n1. Этап 1   2. Этап 2   3. Этап 3   4. Очередь   5. Выход");
            Integer choice = readInt(in, "Выбор: ");
            if (choice == null) {
                save(s);
                return;
            }
            switch (choice) {
                case 1: stage1(s); break;
                case 2: stage2(s); break;
                case 3: stage3(s); break;
                case 4: showQueue(s); break;
                case 5: if (save(s)) return; break;
                default: System.out.println("Введите число от 1 до 5.");
            }
        }
    }

    // A1: приоритет не ниже 3 и потребность не более 8 листов.
    private static void stage1(PrintState s) {
        if (s.lastStage >= 1) {
            System.out.println("Этап 1 уже выполнен.");
            return;
        }
        if (s.stageA != 1) {
            System.out.println("Для этой версии нужен A1.");
            return;
        }

        Predicate<PrintJob> rule =
                j -> j.getPriority() >= 3 && j.getRequiredSheets() <= 8;
        List<PrintJob> selected = sourceJobs().stream()
                .filter(rule)
                .sorted(Comparator.comparingInt(PrintJob::getId))
                .collect(Collectors.toList());

        selected.forEach(s.queue::add);
        List<Integer> ids = selected.stream()
                .map(PrintJob::getId)
                .sorted()
                .collect(Collectors.toList());
        int sheets = selected.stream().mapToInt(PrintJob::getRequiredSheets).sum();

        s.lastStage = 1;
        System.out.println("Добавлены ID: " + ids + "; потребность: " + sheets);
        try {
            s.stageB = StageAccess.second(s.n, ids, sheets);
            System.out.println("Назначен вариант Б" + s.stageB);
        } catch (IllegalStateException e) {
            s.stageB = 0;
            System.out.println("StageAccess отклонил результат: " + e.getMessage());
        }
    }

    // B6: владельцы с меньшим числом прежних печатей идут первыми.
    private static void stage2(PrintState s) {
        if (s.lastStage >= 2) {
            System.out.println("Этап 2 уже выполнен.");
            return;
        }
        if (s.lastStage < 1 || s.stageB == 0) {
            System.out.println("Сначала выполните этап 1.");
            return;
        }
        if (s.stageB != 6) {
            System.out.println("Для этого варианта нет реализации.");
            return;
        }

        List<PrintJob> done =
                s.queue.process(j -> true, b6Order(s.queue), PrintJob::markPrinted);
        s.lastStage = 2;

        List<Integer> ids = done.stream()
                .map(PrintJob::getId)
                .collect(Collectors.toList());
        System.out.println("Напечатаны ID: " + ids
                + "; бумаги осталось: " + s.queue.getPaper());

        try {
            s.stageV = StageAccess.third(s.n, ids, s.queue.getPaper());
            System.out.println("Назначен вариант В" + s.stageV);
        } catch (IllegalStateException e) {
            s.stageV = 0;
            System.out.println("StageAccess отклонил результат: " + e.getMessage());
        }
    }

    // V2: добавить 5 листов, повторно обработать очередь и вывести отчёт через Stream API.
    private static void stage3(PrintState s) {
        if (s.lastStage >= 3) {
            System.out.println("Этап 3 уже выполнен.");
            return;
        }
        if (s.lastStage < 2 || s.stageV == 0) {
            System.out.println("Сначала выполните этап 2.");
            return;
        }
        if (s.stageB != 6 || s.stageV != 2) {
            System.out.println("Для этой версии нужна связка Б6/В2.");
            return;
        }
        if (!s.queue.restock(5)) {
            System.out.println("Не удалось пополнить бумагу.");
            return;
        }

        List<PrintJob> done =
                s.queue.process(j -> true, b6Order(s.queue), PrintJob::markPrinted);
        s.lastStage = 3;

        System.out.println("Новые ID: " + done.stream()
                .map(PrintJob::getId).collect(Collectors.toList()));
        System.out.println("Остаток бумаги: " + s.queue.getPaper());

        Map<Integer, List<PrintJob>> owners = s.queue.getAnalyticsStream()
                .collect(Collectors.groupingBy(PrintJob::getOwnerId));
        List<Integer> report = owners.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(PrintJob::isPrinted)
                        && e.getValue().stream().anyMatch(j -> !j.isPrinted()))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        System.out.println("Владельцы с напечатанными и ожидающими заданиями: " + report);
    }

    private static Comparator<PrintJob> b6Order(PrintQueue<PrintJob> q) {
        // Число прежних печатей фиксируется в начале вызова process().
        Map<Integer, Long> history = q.getAnalyticsStream()
                .filter(PrintJob::isPrinted)
                .collect(Collectors.groupingBy(PrintJob::getOwnerId, Collectors.counting()));

        return Comparator
                .comparingLong((PrintJob j) -> history.getOrDefault(j.getOwnerId(), 0L))
                .thenComparing(Comparator.comparingInt(PrintJob::getPriority).reversed())
                .thenComparingInt(PrintJob::getId);
    }

    private static List<PrintJob> sourceJobs() {
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
                new SingleSidedJob(110, 15, 8, 1, 2));
    }

    private static void showQueue(PrintState s) {
        s.queue.getAnalyticsStream()
                .sorted(Comparator.comparingInt(PrintJob::getId))
                .map(PrintJob::getDetails)
                .forEach(System.out::println);
        System.out.println("Бумаги осталось: " + s.queue.getPaper());
    }

    private static Integer readInt(Scanner in, String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!in.hasNextLine()) return null;
            try {
                return Integer.parseInt(in.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Введите целое число.");
            }
        }
    }

    private static boolean yesNo(Scanner in, String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!in.hasNextLine()) return false;
            String answer = in.nextLine().trim().toLowerCase();
            if (answer.equals("д") || answer.equals("да")) return true;
            if (answer.equals("н") || answer.equals("нет")) return false;
            System.out.println("Ответьте д или н.");
        }
    }

    private static boolean save(PrintState s) {
        try (ObjectOutputStream out =
                     new ObjectOutputStream(new FileOutputStream(FILE.toFile()))) {
            out.writeObject(s);
            System.out.println("Состояние сохранено.");
            return true;
        } catch (IOException e) {
            System.out.println("Ошибка записи: " + e.getMessage()
                    + ". Данные остаются в меню.");
            return false;
        }
    }

    private static final class PrintState implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int n, stageA;
        private int stageB, stageV, lastStage;
        private final PrintQueue<PrintJob> queue = new PrintQueue<>(INITIAL_PAPER);

        private PrintState(int n) {
            this.n = n;
            stageA = StageAccess.initial(n);
        }

        private void validate() throws InvalidObjectException {
            if (n != 5 || stageA != StageAccess.initial(n)
                    || stageB < 0 || stageB > 6 || stageV < 0 || stageV > 6
                    || lastStage < 0 || lastStage > 3 || queue == null) {
                throw new InvalidObjectException("Сохранение не соответствует N=5.");
            }
        }
    }
}

interface PaperDemand {
    int getRequiredSheets();
}

abstract class PrintJob implements PaperDemand, Serializable {
    private static final long serialVersionUID = 1L;
    private final int id, ownerId, pages, copies, priority;
    private boolean printed;

    public PrintJob(int id, int ownerId, int pages, int copies, int priority) {
        if (id <= 0 || ownerId <= 0)
            throw new IllegalArgumentException("ID должны быть положительными.");
        if (pages < 1 || pages > 100 || copies < 1 || copies > 100)
            throw new IllegalArgumentException("Страницы и копии должны быть от 1 до 100.");
        if (priority < 1 || priority > 5)
            throw new IllegalArgumentException("Приоритет должен быть 1..5.");

        this.id = id;
        this.ownerId = ownerId;
        this.pages = pages;
        this.copies = copies;
        this.priority = priority;
    }

    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public int getPages() { return pages; }
    public int getCopies() { return copies; }
    public int getPriority() { return priority; }
    public boolean isPrinted() { return printed; }
    public void markPrinted() { printed = true; }
    public abstract String getDetails();

    protected String details() {
        return "id=" + id + ", owner=" + ownerId
                + ", sheets=" + getRequiredSheets()
                + ", status=" + (printed ? "напечатано" : "ожидает");
    }
}

final class SingleSidedJob extends PrintJob {
    private static final long serialVersionUID = 1L;

    public SingleSidedJob(int id, int owner, int pages, int copies, int priority) {
        super(id, owner, pages, copies, priority);
    }

    public int getRequiredSheets() { return getPages() * getCopies(); }
    public String getDetails() { return "Одностороннее: " + details(); }
}

final class DoubleSidedJob extends PrintJob {
    private static final long serialVersionUID = 1L;

    public DoubleSidedJob(int id, int owner, int pages, int copies, int priority) {
        super(id, owner, pages, copies, priority);
    }

    public int getRequiredSheets() { return ((getPages() + 1) / 2) * getCopies(); }
    public String getDetails() { return "Двустороннее: " + details(); }
}

class PrintQueue<T extends PrintJob> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_PAPER = 1_000_000;
    private List<T> jobs = new ArrayList<>();
    private int paper;

    public PrintQueue(int paper) {
        if (paper < 0 || paper > MAX_PAPER)
            throw new IllegalArgumentException("Некорректный запас бумаги.");
        this.paper = paper;
    }

    public boolean add(T job) {
        if (job == null || job.isPrinted()
                || jobs.stream().anyMatch(j -> j.getId() == job.getId())) return false;
        return jobs.add(job);
    }

    public int getPaper() { return paper; }

    public boolean restock(int amount) {
        if (amount <= 0 || amount > MAX_PAPER - paper) return false;
        paper += amount;
        return true;
    }

    public Stream<T> getAnalyticsStream() { return jobs.stream(); }

    public List<T> process(Predicate<T> condition, Comparator<T> order, Consumer<T> action) {
        if (condition == null || order == null || action == null)
            throw new IllegalArgumentException("Аргументы process не должны быть null.");

        List<T> pending = jobs.stream()
                .filter(j -> !j.isPrinted())
                .sorted(order)
                .collect(Collectors.toList());
        List<T> done = new ArrayList<>();

        for (T job : pending) {
            if (condition.test(job) && job.getRequiredSheets() <= paper) {
                paper -= job.getRequiredSheets();
                action.accept(job);
                done.add(job);
            }
        }
        return done;
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