import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class OrderProcessor {
    public static void main(String[] args) {
        // 1) Prepare catalog
        Map<String, Product> catalog = new ConcurrentHashMap<>();
        catalog.put("P01", new Product("P01", "Ноутбук", 150000.0, 10));
        catalog.put("P02", new Product("P02", "Мышь", 3500.0, 50));
        catalog.put("P03", new Product("P03", "Клавиатура", 7000.0, 30));
        catalog.put("P04", new Product("P04", "Монитор", 45000.0, 5));

        // 2) Executor and scheduler
        int threads = Math.min(20, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        OrderProcessingService service = new OrderProcessingService(catalog, executor, scheduler);

        // 3) Create test orders
        List<Order> orders = Arrays.asList(
                new Order("ORD001", "P01", 1, "cust1@example.com"),
                new Order("ORD002", "P02", 3, "cust2@example.com"),
                new Order("ORD003", "P03", 2, "cust3@example.com"),
                new Order("ORD004", "P01", 2, "cust4@example.com"),
                new Order("ORD005", "P04", 1, "cust5@example.com"),
                new Order("ORD006", "P99", 1, "cust6@example.com"), // not exists -> error
                new Order("ORD007", "P02", 60, "cust7@example.com") // insufficient stock -> error
        );

        System.out.println("=== Запуск обработки заказов (параллельно) ===");
        long start = System.currentTimeMillis();

        // 4) Process all orders in parallel and collect CompletableFutures
        List<CompletableFuture<OrderResult>> futures = orders.stream()
                .map(service::processOrder)
                .collect(Collectors.toList());

        // 5) Wait for all to complete
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            // block until all done (with a safety timeout)
            all.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.out.println("Ошибка ожидания завершения всех задач: " + e.getMessage());
        }

        // 6) Gather results
        List<OrderResult> results = futures.stream()
                .map(CompletableFuture::join) // join is safe because allOf completed (or we caught timeout)
                .collect(Collectors.toList());

        long elapsed = System.currentTimeMillis() - start;

        // 7) Print summary
        System.out.println();
        System.out.println("=== Итоговые результаты ===");
        results.forEach(r -> System.out.println(r.toString()));

        long successCount = results.stream().filter(r -> r.success).count();
        long failCount = results.size() - successCount;
        double totalSum = results.stream().filter(r -> r.success).mapToDouble(r -> r.amount).sum();

        System.out.println();
        System.out.println("Всего заказов: " + results.size());
        System.out.println("Успешных: " + successCount);
        System.out.println("Неуспешных: " + failCount);
        System.out.printf("Общая сумма успешных заказов: %.2f%n", totalSum);
        System.out.printf("Общее время обработки: %d ms%n", elapsed);

        // 8) Shutdown
        service.shutdown();
        System.out.println("Executor services shutdown initiated.");
    }
}
