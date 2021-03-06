package com.meinil.fxmusic.controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import com.leewyatt.rxcontrols.controls.RXAudioSpectrum;
import com.leewyatt.rxcontrols.controls.RXLrcView;
import com.leewyatt.rxcontrols.controls.RXMediaProgressBar;
import com.leewyatt.rxcontrols.controls.RXToggleButton;
import com.leewyatt.rxcontrols.pojo.LrcDoc;
import com.meinil.fxmusic.cell.MusicListCell;
import com.meinil.fxmusic.utils.EncodingDetect;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.AudioSpectrumListener;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class PlayerController {
    @FXML
    private ResourceBundle resources;
    @FXML
    private URL location;
    @FXML
    private AnchorPane drawerPane;
    @FXML
    private BorderPane sliderPane;
    @FXML
    private StackPane soundBtn;
    @FXML
    private StackPane skinBtn;
    @FXML
    private ListView<File> listView;
    @FXML
    private RXAudioSpectrum audioSpectrum;
    @FXML
    private RXLrcView lrcView;
    @FXML
    private RXMediaProgressBar progressBar;
    @FXML
    private ToggleButton playBtn;
    @FXML
    private Label timeLabel;

    // ????????????????????????
    private Timeline showAnim = new Timeline();
    // ????????????????????????
    private Timeline hideAnim = new Timeline();
    // ???????????? ?????????
    private ContextMenu soundPopup;
    private Slider soundSlider;
    // ????????????
    private ContextMenu skinPopup;
    // ????????????
    private MediaPlayer player;
    // ??????????????????
    private EventHandler<Event> progressBarHandler;


    // ??????????????????
    private final float[] DEFAULT_SPECTRUM = new float[128];
    private final SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");

    // ???????????????
    private AudioSpectrumListener audioSpectrumListener = (timestamp, duration, magnitudes, phases) -> audioSpectrum.setMagnitudes(magnitudes);
    // ??????????????????
    private ChangeListener<Duration> durationChangeListener = (observable, oldValue, newValue) -> {
        progressBar.setCurrentTime(newValue);
        String currentTime = sdf.format(newValue.toMillis());
        String total = sdf.format(player.getBufferProgressTime().toMillis());
        timeLabel.setText(String.format("%s/%s", currentTime, total));
    };


    @FXML
    public void initialize() {
        initAnim();

        initSoundPopup();

        initSkinPopup();

        // ????????????
        Arrays.fill(DEFAULT_SPECTRUM, -60.0f);

        initListView();

        initProgressBar();
    }

    /**
     * ?????????????????????
     */
    public void initSkinPopup() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/skin.fxml"));
            Parent skinRoot = fxmlLoader.load();
            skinPopup = new ContextMenu(new SeparatorMenuItem());
            skinPopup.getScene().setRoot(skinRoot);

            ObservableMap<String, Object> namespace = fxmlLoader.getNamespace();
            ToggleGroup group = (ToggleGroup)namespace.get("skinGroup");
            group.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
                @Override
                public void changed(ObservableValue<? extends Toggle> observable, Toggle oldValue, Toggle newValue) {
                    RXToggleButton btn = (RXToggleButton) newValue;
                    String skin = btn.getText();
                    drawerPane.getScene()
                            .getRoot()
                            .getStylesheets()
                            .setAll(getClass().getResource("/css/" + skin + ".css").toExternalForm());

                    skinPopup.getScene()
                            .getRoot()
                            .getStylesheets()
                            .setAll(getClass().getResource("/css/" + skin + ".css").toExternalForm());
                    soundPopup.getScene()
                            .getRoot()
                            .getStylesheets()
                            .setAll(getClass().getResource("/css/" + skin + ".css").toExternalForm());
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ?????????????????????
     */
    public void initSoundPopup() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/sound.fxml"));
            Parent soundRoot = fxmlLoader.load();

            ObservableMap<String, Object> namespace = fxmlLoader.getNamespace();
            soundSlider = (Slider) namespace.get("soundSlider");
            Label soundNum = (Label) namespace.get("soundNum");
            soundNum.textProperty().bind(soundSlider.valueProperty().asString("%.0f%%"));

            soundPopup = new ContextMenu(new SeparatorMenuItem());
            soundPopup.getScene().setRoot(soundRoot);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ???????????????
     */
    public void initAnim() {
        showAnim = new Timeline(new KeyFrame(Duration.millis(300), new KeyValue(sliderPane.translateXProperty(), 0)));
        hideAnim = new Timeline(new KeyFrame(Duration.millis(300), new KeyValue(sliderPane.translateXProperty(), 300)));
        hideAnim.setOnFinished(event -> {
            drawerPane.setVisible(false);
        });
    }

    /**
     * ??????????????????
     */
    public void initProgressBar() {
        // ?????????????????????
        progressBarHandler = event -> {
            if (player != null) {
                player.seek(progressBar.getCurrentTime());
            }
        };
        progressBar.setOnMouseClicked(progressBarHandler);
        progressBar.setOnDragDone(progressBarHandler);
    }

    /**
     * ?????????listView
     */
    public void initListView() {
        listView.setCellFactory(param -> new MusicListCell());
        listView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (player != null) {
                    disposePlayer();
                }
                player = new MediaPlayer(new Media(newValue.toURI().toString()));
                player.volumeProperty().bind(soundSlider.valueProperty().divide(100));  // ????????????

                // ????????????
                String lrcUrl = newValue.getAbsolutePath().replaceAll("mp3$", "lrc");
                File lrcFile = new File(lrcUrl);
                if (lrcFile.exists()) {
                    try {
                        byte[] bytes = Files.readAllBytes(lrcFile.toPath());
                        lrcView.setLrcDoc(LrcDoc.parseLrcDoc(new String(bytes, EncodingDetect.detect(bytes))));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                lrcView.currentTimeProperty().bind(player.currentTimeProperty());

                // ???????????????
                player.setAudioSpectrumListener(audioSpectrumListener);

                // ????????????????????????
                progressBar.durationProperty().bind(player.getMedia().durationProperty());
                // ?????????????????? ???????????????????????????????????????????????????
                player.currentTimeProperty().addListener(durationChangeListener);

                // ?????????????????????????????????
                player.setOnEndOfMedia(() -> {
                    onPlayNextAction(null);
                });

                player.play();
                playBtn.setSelected(true);
            } else {
                if (player != null) {
                    disposePlayer();
                }
            }
        });
    }

    /**
     * Player??????
     */
    private void disposePlayer() {
        player.stop();                           // ????????????
        playBtn.setSelected(false);
        lrcView.setLrcDoc(null);                // ????????????
        lrcView.currentTimeProperty().unbind(); // ??????????????????
        lrcView.setCurrentTime(Duration.ZERO);  // ??????????????????
        player.setAudioSpectrumListener(null);  // ????????????????????????null
        progressBar.durationProperty().unbind(); // ?????????
        progressBar.setCurrentTime(Duration.ZERO);
        player.currentTimeProperty().removeListener(durationChangeListener);
        player.setAudioSpectrumListener(null);
        audioSpectrum.setMagnitudes(DEFAULT_SPECTRUM);
        timeLabel.setText("00:00/00:00");
        player.setOnEndOfMedia(null);
        player.dispose();                       // ??????
        player = null;
    }

    /**
     * ?????????????????????
     * @param event ?????????????????????
     */
    @FXML
    public void onHideSliderPaneAction(MouseEvent event) {
        if (showAnim.getStatus().equals(Animation.Status.RUNNING)) {
            showAnim.stop();
        }
        hideAnim.play();
    }

    /**
     * ?????????????????????
     * @param event ?????????????????????
     */
    @FXML
    public void onShowSliderPaneAction(MouseEvent event) {
        if (hideAnim.getStatus().equals(Animation.Status.RUNNING)) {
            hideAnim.stop();
        }
        drawerPane.setVisible(true);
        showAnim.play();
    }

    /**
     * ????????????
     * @param event ?????????????????????
     */
    @FXML
    public void onCloseAction(MouseEvent event) {
        Platform.exit();
    }

    /**
     * ?????????
     * @param event ?????????????????????
     */
    @FXML
    public void onMiniAction(MouseEvent event) {
        Stage stage = (Stage)drawerPane.getScene().getWindow();
        stage.setIconified(true);
    }

    /**
     * ?????????
     * @param event ?????????????????????
     */
    @FXML
    public void onFullAction(MouseEvent event) {
        Stage stage = findStage();
        stage.setFullScreen(!stage.isFullScreen());
    }

    /**
     * ??????????????????
     * @param event ?????????????????????
     */
    @FXML
    public void onSoundPopupAction(MouseEvent event) {
        Stage stage = findStage();
        Bounds bounds = soundBtn.localToScreen(soundBtn.getBoundsInLocal());
        soundPopup.show(stage, bounds.getMinX() - 19, bounds.getMinY() - 168);
    }

    /**
     * ??????????????????
     */
    @FXML
    public void onSkinPopupAction(MouseEvent event) {
        Stage stage = findStage();
        Bounds bounds = skinBtn.localToScreen(skinBtn.getBoundsInLocal());
        skinPopup.show(stage, bounds.getMinX() - 120, bounds.getMaxY() + 10);
    }

    /**
     * ??????????????????
     * @param event ?????????????????????
     */
    @FXML
    public void onAddMusicAction(MouseEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("mp3", "*.mp3"));
        List<File> files = fileChooser.showOpenMultipleDialog(findStage());
        ObservableList<File> items = listView.getItems();
        if (files != null) {
            files.forEach(file -> {
                if (!items.contains(file)) {
                    items.add(file);
                }
            });
        }
    }

    /**
     * ???????????????
     * @param event ?????????????????????
     */
    @FXML
    public void onPlayPrevAction(MouseEvent event) {
        int size = listView.getItems().size();
        if (size < 2) {
            return;
        }
        int index = listView.getSelectionModel().getSelectedIndex();
        index = (index == 0) ? size - 1 : index - 1;
        listView.getSelectionModel().select(index);
    }

    /**
     * ??????/????????????
     * @param event ?????????????????????
     */
    @FXML
    public void onPlayAction(MouseEvent event) {
        if(player != null) {
            if (playBtn.isSelected()) {
                player.play();
            } else {
                player.pause();
            }
        } else {
            playBtn.setSelected(false);
        }
    }

    /**
     * ???????????????
     * @param event ?????????????????????
     */
    @FXML
    public void onPlayNextAction(MouseEvent event) {
        int size = listView.getItems().size();
        if (size < 2) {
            return;
        }
        int index = listView.getSelectionModel().getSelectedIndex();
        index = (index == size - 1) ? 0 : index + 1;
        listView.getSelectionModel().select(index);
    }

    /**
     * ????????????
     * @return ??????
     */
    private Stage findStage() {
        return (Stage) drawerPane.getScene().getWindow();
    }
}
