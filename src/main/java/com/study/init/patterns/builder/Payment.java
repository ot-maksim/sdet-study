package com.study.init.patterns.builder;

import java.math.BigDecimal;

/*
Обычный билдер проверяет обязательные поля только в рантайме, в build().
Step builder заставляет компилятор следить за порядком: пока не задашь обязательные поля, метода build() просто нет.
 */

public class Payment {
    private final String account;
    private final BigDecimal amount;
    private final String comment;

    private Payment(String account, BigDecimal amount, String comment) {
        this.account = account;
        this.amount = amount;
        this.comment = comment;
    }

    public static AccountStep builder() {
        return new Steps();
    }

    public interface AccountStep {
        AmountStep account(String account);
    }

    public interface AmountStep {
        FinalStep amount(BigDecimal amount);
    }

    public interface FinalStep {
        FinalStep comment(String comment);
        Payment build();
    }

    private static final class Steps implements AccountStep, AmountStep, FinalStep {
        private final String account;
        private final BigDecimal amount;
        private final String comment;

        private Steps() {
            this(null, null, null);
        }

        private Steps(String account, BigDecimal amount, String comment) {
            this.account = account;
            this.amount = amount;
            this.comment = comment;
        }

        @Override
        public AmountStep account(String account) {
            return new Steps(account, amount, comment);
        }

        @Override
        public FinalStep amount(BigDecimal amount) {
            return new Steps(account, amount, comment);
        }

        @Override
        public FinalStep comment(String comment) {
            return new Steps(account, amount, comment);
        }

        @Override
        public Payment build() {
            return new Payment(account, amount, comment);
        }
    }

    @Override
    public String toString() {
        return "Payment{" +
                "account='" + account + '\'' +
                ", amount=" + amount +
                ", comment='" + comment + '\'' +
                '}';
    }

    public static void main(String[] args) {
        Payment p = Payment.builder().account("PL61...").amount(new BigDecimal("100")).comment("").build();
// Payment.builder().amount(...)  — не скомпилируется: после builder() доступен только account()
        Payment payment = Payment.builder().account(null).amount(new BigDecimal("100")).comment("treter").comment("sdfs").build();
        System.out.println(p);
        System.out.println(payment);

        AmountStep base = Payment.builder().account("PL61...");
        Payment a = base.amount(new BigDecimal("100")).comment("rent").build();
        Payment b = base.amount(new BigDecimal("500")).build();
        System.out.println(a);
        System.out.println(b);
    }
}
