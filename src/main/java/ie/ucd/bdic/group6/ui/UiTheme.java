package ie.ucd.bdic.group6.ui;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;

import java.util.Objects;

public final class UiTheme {

    private static final String APP_STYLESHEET = "/css/style.css";

    public static void applyDefaultTheme() {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
    }

    public static String appStylesheet() {
        return Objects.requireNonNull(UiTheme.class.getResource(APP_STYLESHEET)).toExternalForm();
    }
}
