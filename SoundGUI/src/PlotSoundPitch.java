import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;

import javafx.scene.chart.XYChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.MathArrays;

import jp.ac.kyoto_u.kuis.le4music.Le4MusicUtils;
import jp.ac.kyoto_u.kuis.le4music.LineChartWithSpectrogram;

import java.io.IOException;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class PlotSoundPitch extends Application {

@Override public final void start(final Stage primaryStage)
throws IOException,
UnsupportedAudioFileException {
/* コ マ ン ド ラ イ ン 引 数 処 理 */
final String[] args = getParameters().getRaw().toArray(new String[0]);
if (args.length < 1) {
System.out.println("WAVFILE is not given.");
Platform.exit();
return;
}
final File wavFile = new File(args[0]);

final double frameDuration = Le4MusicUtils.frameDuration;
final double shiftDuration = frameDuration / 8.0;

/* W A V フ ァ イ ル 読 み 込 み */
final AudioInputStream stream = AudioSystem.getAudioInputStream(wavFile);
final double[] waveform = Le4MusicUtils.readWaveformMonaural(stream);
final AudioFormat format = stream.getFormat();
final double sampleRate = format.getSampleRate();
stream.close();

/* 窓 関 数 と F F T の サ ン プ ル 数 */
final int frameSize = (int)Math.round(frameDuration * sampleRate);
final int fftSize = 1 << Le4MusicUtils.nextPow2(frameSize);
final int fftSize2 = (fftSize >> 1) + 1;

/* シ フ ト の サ ン プ ル 数 */
final int shiftSize = (int)Math.round(shiftDuration * sampleRate);

/* 窓 関 数 を 求 め 正 規 化 す る */
final double[] window = MathArrays.normalizeArray(
Arrays.copyOf(Le4MusicUtils.hanning(frameSize), fftSize), 1.0
);

/* 短 時 間 フ ー リ エ 変 換 本 体 */
final Stream<Complex[]> spectrogram =
Le4MusicUtils.sliding(waveform, window, shiftSize)
.map(frame -> Le4MusicUtils.rfft(frame));

/* 複 素 ス ペ ク ト ロ グ ラ ム を 対 数 振 幅 ス ペ ク ト ロ グ ラ ム に */
final double[][] specLog =
spectrogram.map(sp -> Arrays.stream(sp)
.mapToDouble(c -> 2 * Math.log10(c.abs()))
.toArray())
.toArray(n -> new double[n][]);

/* 各スペクトラムをケプトラムに変換 */
double[][] cepstrums = new double[specLog.length][specLog[0].length];
for(int i = 0; i < specLog.length; i++) {
	double[] s = Arrays.copyOfRange(specLog[i],0,specLog[i].length-1);
	Complex[] cepstrum = Le4MusicUtils.fft(s);
	cepstrums[i] = Arrays.stream(cepstrum)
			.mapToDouble(c -> c.getReal())
			.toArray();
}

System.out.print(cepstrums.length);
final ObservableList<XYChart.Data<Number, Number>> data =
IntStream.range(1,cepstrums[0].length)
.mapToObj(i -> new XYChart.Data<Number, Number>(i * frameDuration, cepstrums[4][i]))
.collect(Collectors.toCollection(FXCollections::observableArrayList));

/* デ ー タ 系 列 に 名 前 を つ け る */
final XYChart.Series<Number, Number> series = new XYChart.Series<>("Spectrum", data);

/* 軸 を 作 成 */
final NumberAxis xAxis = new NumberAxis();
xAxis.setLabel("Frequency (Hz)");
final NumberAxis yAxis = new NumberAxis();
yAxis.setLabel("Amplitude (dB)");

/* チ ャ ー ト を 作 成 */
final LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
chart.setTitle("Spectrum");
chart.setCreateSymbols(false);
chart.getData().add(series);

/* グ ラ フ 描 画 */
final Scene scene = new Scene(chart, 800, 600);

/* ウ イ ン ド ウ 表 示 */
primaryStage.setScene(scene);
primaryStage.setTitle(getClass().getName());
primaryStage.show();
}
}
