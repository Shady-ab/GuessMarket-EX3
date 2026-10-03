package guessmarket.client.ui;

import java.util.Locale;

final class UiFormat {
    private UiFormat() {
    }

    static String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    static String moneyOrDash(Double value) {
        return value == null ? "-" : money(value);
    }

    static String signedMoney(double value) {
        return (value > 0 ? "+" : "") + money(value);
    }

    static String commission(int percentage, String type) {
        return percentage + "% " + type;
    }
}
