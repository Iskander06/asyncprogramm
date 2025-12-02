public class OrderResult {
    public final String orderId;
    public final boolean success;
    public final double amount;
    public final String message;

    public OrderResult(String orderId, boolean success, double amount, String message) {
        this.orderId = orderId;
        this.success = success;
        this.amount = amount;
        this.message = message;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("✓ %s — %.2f — %s", orderId, amount, message);
        } else {
            return String.format("✗ %s — ERROR: %s", orderId, message);
        }
    }
}
