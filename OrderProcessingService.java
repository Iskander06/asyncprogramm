import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

public class OrderProcessingService {
    private final Map<String, Product> catalog;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final Random rnd = new Random();

    // Timeouts and delays in milliseconds
    private static final long CHECK_AVAIL_MS = 1000;
    private static final long CALC_PRICE_MS = 500;
    private static final long PROCESS_PAYMENT_MS = 2000;
    private static final long RESERVE_MS = 800;
    private static final long NOTIFY_MS = 1000;
    private static final long ORDER_TIMEOUT_MS = 10_000; // overall order timeout

    public OrderProcessingService(Map<String, Product> catalog, ExecutorService executor, ScheduledExecutorService scheduler) {
        this.catalog = catalog;
        this.executor = executor;
        this.scheduler = scheduler;
    }

    // Helper: create a timeout future that completes exceptionally after timeout
    private <T> CompletableFuture<T> timeoutAfter(long timeoutMillis) {
        CompletableFuture<T> f = new CompletableFuture<>();
        scheduler.schedule(() -> f.completeExceptionally(new TimeoutException("Operation timed out after " + timeoutMillis + " ms")), timeoutMillis, TimeUnit.MILLISECONDS);
        return f;
    }

    public CompletableFuture<Product> checkProductAvailability(Order order) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(CHECK_AVAIL_MS);
            Product p = catalog.get(order.productId);
            if (p == null) {
                throw new RuntimeException("Товар " + order.productId + " не найден");
            }
            synchronized (p) { // read stock under lock for consistency
                if (p.stock < order.quantity) {
                    throw new RuntimeException("Недостаточно товара на складе для " + order.orderId);
                }
            }
            System.out.printf("[%s] Товар найден: %s (в наличии: %d)%n", order.orderId, p.name, p.stock);
            return p;
        }, executor);
    }

    public CompletableFuture<Double> calculatePrice(Order order, Product product) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(CALC_PRICE_MS);
            double base = product.price * order.quantity;
            double discount = (order.quantity > 5) ? base * 0.10 : 0.0; // 10% discount for qty>5
            double taxed = (base - discount) * 1.12; // +12% tax
            double result = Math.round(taxed * 100.0) / 100.0;
            System.out.printf("[%s] Расчет цены: base=%.2f discount=%.2f total=%.2f%n", order.orderId, base, discount, result);
            return result;
        }, executor);
    }

    public CompletableFuture<Boolean> processPayment(Order order, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(PROCESS_PAYMENT_MS);
            // simulate 10% payment failure
            boolean success = rnd.nextDouble() > 0.10;
            if (!success) {
                throw new RuntimeException("Оплата не прошла для " + order.orderId);
            }
            System.out.printf("[%s] Платёж успешно обработан: %.2f%n", order.orderId, amount);
            return true;
        }, executor);
    }

    public CompletableFuture<Void> reserveProduct(Order order, Product product) {
        return CompletableFuture.runAsync(() -> {
            sleep(RESERVE_MS);
            synchronized (product) {
                if (product.stock < order.quantity) {
                    throw new RuntimeException("Во время резервирования товара обнаружен недостаток для " + order.orderId);
                }
                product.stock -= order.quantity;
            }
            System.out.printf("[%s] Резервирование завершено. Оставшийся сток для %s: %d%n", order.orderId, product.id, product.stock);
        }, executor);
    }

    public CompletableFuture<Void> sendNotification(Order order, boolean success, double amount) {
        return CompletableFuture.runAsync(() -> {
            sleep(NOTIFY_MS);
            String body = success
                    ? String.format("Ваш заказ %s успешно обработан. Сумма: %.2f", order.orderId, amount)
                    : String.format("Ваш заказ %s не может быть обработан: %s", order.orderId, "см. детали");
            // simulate email send
            System.out.printf("[%s] Отправлено уведомление на %s: %s%n", order.orderId, order.email, body);
        }, executor);
    }

    // The full pipeline for a single order with manual overall timeout
    public CompletableFuture<OrderResult> processOrder(Order order) {
        CompletableFuture<OrderResult> pipeline = checkProductAvailability(order)
                .thenCompose(product ->
                        calculatePrice(order, product)
                                .thenCompose(price ->
                                        processPayment(order, price)
                                                .thenCompose(ignore -> reserveProduct(order, product)
                                                        .thenCompose(ignore2 -> sendNotification(order, true, price)
                                                                .thenApply(ignore3 -> new OrderResult(order.orderId, true, price, "Заказ успешно обработан"))
                                                        )
                                                )
                                )
                )
                .handle((result, ex) -> {
                    if (ex == null) {
                        return result;
                    } else {
                        // If something failed, try to notify user about failure (best-effort, do not throw)
                        String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                        // Attempt to send failure notification (best-effort, non-blocking)
                        sendNotification(order, false, 0.0)
                                .exceptionally(nex -> { System.out.printf("[%s] Не удалось отправить уведомление об ошибке: %s%n", order.orderId, nex.getMessage()); return null; });
                        return new OrderResult(order.orderId, false, 0.0, message);
                    }
                });

        // Apply overall timeout: anyOf(original, timeoutFuture) and map the timeout to an exception result
        CompletableFuture<OrderResult> timeout = timeoutAfter(ORDER_TIMEOUT_MS);
        CompletableFuture<Object> first = CompletableFuture.anyOf(pipeline, timeout);
        return first.thenApply(obj -> {
            if (obj instanceof OrderResult) return (OrderResult) obj;
            else return new OrderResult(order.orderId, false, 0.0, "Таймаут выполнения заказа");
        });
    }

    // utility sleep wrapper
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    public void shutdown() {
        executor.shutdown();
        scheduler.shutdown();
    }
}
