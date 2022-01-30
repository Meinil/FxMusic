/**
 * @Author Meinil
 * @Version 1.0
 */
module FxMusic {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires rxcontrols;

    opens com.meinil.fxmusic.controller to javafx.fxml;

    exports com.meinil.fxmusic;
}