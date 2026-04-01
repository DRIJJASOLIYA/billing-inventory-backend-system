package com.example.BillGeneration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Billing billing = new Billing();
    private final Messages messages = new Messages();
    private final Resilience resilience = new Resilience();

    public Billing getBilling() {
        return billing;
    }

    public Messages getMessages() {
        return messages;
    }

    public Resilience getResilience() {
        return resilience;
    }

    public static class Billing {
        private BigDecimal gstRate = new BigDecimal("0.18");

        public BigDecimal getGstRate() {
            return gstRate;
        }

        public void setGstRate(BigDecimal gstRate) {
            this.gstRate = gstRate;
        }
    }

    public static class Messages {
        private String orderSuccessText = "Payment Successful. Order Placed Successfully";
        private String orderFailureText = "Payment Failed. Please try again";
        private String orderSmsTemplate = "Hello %s, your order is confirmed. Order ID: %d, Bill No: %s, Items: %d, Payment: %s, Amount: Rs %s. Thank you.";
        private String lowStockSubject = "Low Stock Alert";
        private String lowStockTemplate = "Product: %s\nRemaining Stock: %d\nThreshold Quantity: %d";

        public String getOrderSuccessText() {
            return orderSuccessText;
        }

        public void setOrderSuccessText(String orderSuccessText) {
            this.orderSuccessText = orderSuccessText;
        }

        public String getOrderFailureText() {
            return orderFailureText;
        }

        public void setOrderFailureText(String orderFailureText) {
            this.orderFailureText = orderFailureText;
        }

        public String getOrderSmsTemplate() {
            return orderSmsTemplate;
        }

        public void setOrderSmsTemplate(String orderSmsTemplate) {
            this.orderSmsTemplate = orderSmsTemplate;
        }

        public String getLowStockSubject() {
            return lowStockSubject;
        }

        public void setLowStockSubject(String lowStockSubject) {
            this.lowStockSubject = lowStockSubject;
        }

        public String getLowStockTemplate() {
            return lowStockTemplate;
        }

        public void setLowStockTemplate(String lowStockTemplate) {
            this.lowStockTemplate = lowStockTemplate;
        }
    }

    public static class Resilience {
        private int failureThreshold = 3;
        private long openStateSeconds = 60;

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public long getOpenStateSeconds() {
            return openStateSeconds;
        }

        public void setOpenStateSeconds(long openStateSeconds) {
            this.openStateSeconds = openStateSeconds;
        }
    }
}
