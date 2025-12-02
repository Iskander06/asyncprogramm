public class Order {
    public final String orderId;
    public final String productId;
    public final int quantity;
    public final String email;

    public Order(String orderId, String productId, int quantity, String email) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.email = email;
    }

    @Override
    public String toString() {
        return String.format("Order{id=%s, product=%s, qty=%d, email=%s}",
                orderId, productId, quantity, email);
    }
}
