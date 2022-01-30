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

    // 播放列表显示动画
    private Timeline showAnim = new Timeline();
    // 播放列表隐藏动画
    private Timeline hideAnim = new Timeline();
    // 音量组件 音量条
    private ContextMenu soundPopup;
    private Slider soundSlider;
    // 皮肤组件
    private ContextMenu skinPopup;
    // 播放对象
    private MediaPlayer player;
    // 播放进度调整
    private EventHandler<Event> progressBarHandler;


    // 默认频谱数组
    private final float[] DEFAULT_SPECTRUM = new float[128];
    private final SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");

    // 频谱可视化
    private AudioSpectrumListener audioSpectrumListener = (timestamp, duration, magnitudes, phases) -> audioSpectrum.setMagnitudes(magnitudes);
    // 进度条监听器
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

        // 填充数组
        Arrays.fill(DEFAULT_SPECTRUM, -60.0f);

        initListView();

        initProgressBar();
    }

    /**
     * 初始化皮肤弹窗
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
     * 初始化音量组件
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
     * 初始化动画
     */
    public void initAnim() {
        showAnim = new Timeline(new KeyFrame(Duration.millis(300), new KeyValue(sliderPane.translateXProperty(), 0)));
        hideAnim = new Timeline(new KeyFrame(Duration.millis(300), new KeyValue(sliderPane.translateXProperty(), 300)));
        hideAnim.setOnFinished(event -> {
            drawerPane.setVisible(false);
        });
    }

    /**
     * 初始化进度条
     */
    public void initProgressBar() {
        // 进度条拖动事件
        progressBarHandler = event -> {
            if (player != null) {
                player.seek(progressBar.getCurrentTime());
            }
        };
        progressBar.setOnMouseClicked(progressBarHandler);
        progressBar.setOnDragDone(progressBarHandler);
    }

    /**
     * 初始化listView
     */
    public void initListView() {
        listView.setCellFactory(param -> new MusicListCell());
        listView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (player != null) {
                    disposePlayer();
                }
                player = new MediaPlayer(new Media(newValue.toURI().toString()));
                player.volumeProperty().bind(soundSlider.valueProperty().divide(100));  // 绑定音量

                // 设置歌词
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

                // 频谱可视化
                player.setAudioSpectrumListener(audioSpectrumListener);

                // 设置进度条总时长
                progressBar.durationProperty().bind(player.getMedia().durationProperty());
                // 当前播放时间 不可使用绑定否则会影响下面拖动功能
                player.currentTimeProperty().addListener(durationChangeListener);

                // 播放完成自动播放下一首
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
     * Player销毁
     */
    private void disposePlayer() {
        player.stop();                           // 停止音乐
        playBtn.setSelected(false);
        lrcView.setLrcDoc(null);                // 清空歌词
        lrcView.currentTimeProperty().unbind(); // 解绑歌词组件
        lrcView.setCurrentTime(Duration.ZERO);  // 歌词当前时间
        player.setAudioSpectrumListener(null);  // 频谱可视化设置为null
        progressBar.durationProperty().unbind(); // 进度条
        progressBar.setCurrentTime(Duration.ZERO);
        player.currentTimeProperty().removeListener(durationChangeListener);
        player.setAudioSpectrumListener(null);
        audioSpectrum.setMagnitudes(DEFAULT_SPECTRUM);
        timeLabel.setText("00:00/00:00");
        player.setOnEndOfMedia(null);
        player.dispose();                       // 销毁
        player = null;
    }

    /**
     * 侧边栏隐藏动画
     * @param event 鼠标点击时触发
     */
    @FXML
    public void onHideSliderPaneAction(MouseEvent event) {
        if (showAnim.getStatus().equals(Animation.Status.RUNNING)) {
            showAnim.stop();
        }
        hideAnim.play();
    }

    /**
     * 侧边栏显示动画
     * @param event 鼠标点击时触发
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
     * 窗口关闭
     * @param event 鼠标点击时触发
     */
    @FXML
    public void onCloseAction(MouseEvent event) {
        Platform.exit();
    }

    /**
     * 最小化
     * @param event 鼠标点击时触发
     */
    @FXML
    public void onMiniAction(MouseEvent event) {
        Stage stage = (Stage)drawerPane.getScene().getWindow();
        stage.setIconified(true);
    }

    /**
     * 最大化
     * @param event 鼠标点击时触发
     */
    @FXML
    public void onFullAction(MouseEvent event) {
        Stage stage = findStage();
        stage.setFullScreen(!stage.isFullScreen());
    }

    /**
     * 显示音量组件
     * @param event 鼠标点击时触发
     */
    @FXML
    public void onSoundPopupAction(MouseEvent event) {
        Stage stage = findStage();
        Bounds bounds = soundBtn.localToScreen(soundBtn.getBoundsInLocal());
        soundPopup.show(stage, bounds.getMinX() - 19, bounds.getMinY() - 168);
    }

    /**
     * 更换皮肤弹窗
     */
    @FXML
    public void onSkinPopupAction(MouseEvent event) {
        Stage stage = findStage();
        Bounds bounds = skinBtn.localToScreen(skinBtn.getBoundsInLocal());
        skinPopup.show(stage, bounds.getMinX() - 120, bounds.getMaxY() + 10);
    }

    /**
     * 添加音乐按钮
     * @param event 鼠标松开时触发
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
     * 上一曲按钮
     * @param event 鼠标释放时触发
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
     * 播放/暂停按钮
     * @param event 鼠标释放时触发
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
     * 下一曲按钮
     * @param event 鼠标释放时触发
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
     * 获取舞台
     * @return 舞台
     */
    private Stage findStage() {
        return (Stage) drawerPane.getScene().getWindow();
    }
}
