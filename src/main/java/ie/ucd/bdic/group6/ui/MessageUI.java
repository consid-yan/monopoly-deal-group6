package ie.ucd.bdic.group6.ui;

import javafx.scene.control.TextInputControl;

import java.util.Collection;

public final class MessageUI {
    private MessageUI() {
    }

    static void show(TextInputControl target, String message) {
        if (target == null || message == null || message.isBlank()) {
            return;
        }
        target.setText(message.strip());
    }

    static void showLines(TextInputControl target, Collection<String> lines) {
        if (target == null || lines == null) {
            return;
        }
        String message = String.join("\n", lines.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(String::strip)
                .toList());
        show(target, message);
    }

    static void clear(TextInputControl target) {
        if (target != null) {
            target.clear();
        }
    }
}
