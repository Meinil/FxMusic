package com.meinil.fxmusic.cell;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;

import java.io.File;

/**
 * @Author Meinil
 * @Version 1.0
 */
public class MusicListCell extends ListCell<File> {
    private final Label label;
    private final BorderPane pane;

    public MusicListCell() {
        pane = new BorderPane();

        label = new Label();
        BorderPane.setAlignment(label, Pos.CENTER_LEFT);
        pane.setCenter(label);

        Button button = new Button();
        button.getStyleClass().add("remove-btn");
        button.setGraphic(new Region());
        button.setOnAction(event -> {
            getListView().getItems().remove(getItem());
        });
        pane.setRight(button);

    }

    @Override
    protected void updateItem(File item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
            this.setGraphic(null);
        } else {
            String name = item.getName().substring(0, item.getName().length() - 4);
            label.setText(name);
            this.setGraphic(pane);
        }
    }
}
