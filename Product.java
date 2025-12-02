public class Product {
    public final String id;
    public final String name;
    public final double price;
    public int stock; // mutable to simulate reservation

    public Product(String id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    @Override
    public String toString() {
        return String.format("Product{id=%s, name=%s, price=%.2f, stock=%d}",
                id, name, price, stock);
    }
}
