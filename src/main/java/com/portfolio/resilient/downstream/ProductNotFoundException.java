package com.portfolio.resilient.downstream;

public class ProductNotFoundException extends RuntimeException {

    private final String sku;

    public ProductNotFoundException(String sku) {
        super("No product with sku '" + sku + "' exists in the inventory catalog.");
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
