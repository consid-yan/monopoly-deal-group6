package ie.ucd.bdic.group6.ui;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;

public final class UiTheme {

    private static final String APP_STYLESHEET = "/css/style.css";

    private UiTheme() {
    }

    public static void applyDefaultTheme() {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
    }

    public static String appStylesheet() {
        return UiTheme.class.getResource(APP_STYLESHEET).toExternalForm();
    }
}
