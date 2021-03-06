import java.io.File;
import java.util.Arrays;
import java.util.Optional;
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
final double nyquist = sampleRate * 0.5;

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
.mapToDouble(c -> Math.log10(c.abs()))
.toArray())
.toArray(n -> new double[n][]);

/* 各スペクトラムをケプトラムに変換 */
double[][] cepstrums = new double[specLog.length][];
for(int i = 0; i < specLog.length; i++) {
	double[] s = Arrays.copyOfRange(specLog[i],0,specLog[i].length-1);
	Complex[] cepstrum = Le4MusicUtils.fft(s);
	cepstrums[i] = Arrays.stream(cepstrum)
			.mapToDouble(c -> c.getReal())
			.toArray();
}

final double duration = frameDuration / (cepstrums[0].length - 1);

double[] fundFreqs = new double[cepstrums.length];

for(int i = 0; i < cepstrums.length; i++) {
	double fundFreq = 50;
	int ans = 0;
	for (int j = 10; j < 200; j++) {
		double k = Math.abs(cepstrums[i][j]);
		if(fundFreq < k) {
			fundFreq = k;
			ans = j;
		}
	}
	if (ans < 50 || 1600 < ans) {
		fundFreqs[i] = 0;
	}
	else {
		fundFreqs[i] = 1 / (ans * duration);
	}
}
/*
for(int i = 0; i<cepstrums[160].length;i++) {
	System.out.print(i + "  "+ 1 / (i * duration) + "  " + cepstrums[200][i] +"  " + fundFreqs[200] +"\n");
}
*/
/* デ ー タ 系 列 を 作 成 */

final ObservableList<XYChart.Data<Number, Number>> data =
IntStream.range(0, cepstrums[0].length)
.mapToObj(i -> new XYChart.Data<Number, Number>(i, cepstrums[80][i]))
.collect(Collectors.toCollection(FXCollections::observableArrayList));

/* デ ー タ 系 列 を 作 成 */
/*
final ObservableList<XYChart.Data<Number, Number>> data =
IntStream.range(0,fundFreqs.length)
.mapToObj(i -> new XYChart.Data<Number, Number>(i* shiftDuration,fundFreqs[i]))
.collect(Collectors.toCollection(FXCollections::observableArrayList));
*/
/* デ ー タ 系 列 に 名 前 を つ け る */
final XYChart.Series<Number, Number> series =
new XYChart.Series<>("cepstrum", data);

/* X 軸 を 作 成 */
final double freqLowerBound =(0.0);
final double freqUpperBound = (nyquist);
final NumberAxis xAxis = new NumberAxis(
/* axisLabel = */ "Time",
/* lowerBound = */ 0,
/* upperBound = */ 2048,
/* tickUnit =  Le4MusicUtils.autoTickUnit(freqUpperBound - freqLowerBound)*/ 0.1
);
xAxis.setAnimated(false);

/* Y 軸 を 作 成 */
final double ampLowerBound =(Le4MusicUtils.spectrumAmplitudeLowerBound);

final double ampUpperBound =(Le4MusicUtils.spectrumAmplitudeUpperBound);
final NumberAxis yAxis = new NumberAxis(
/* axisLabel = */ "Amplitude",
/* lowerBound = */ -250,
/* upperBound = */ 250,
/* tickUnit = */ Le4MusicUtils.autoTickUnit(ampUpperBound - ampLowerBound)
);
yAxis.setAnimated(false);

/* チ ャ ー ト を 作 成 */
final LineChart<Number, Number> chart =
new LineChart<>(xAxis, yAxis);
chart.setTitle("Cepstrum");
chart.setCreateSymbols(false);
chart.setLegendVisible(false);
chart.getData().add(series);

/* グ ラ フ 描 画 */
final Scene scene = new Scene(chart, 800, 600);

/* ウ イ ン ド ウ 表 示 */
primaryStage.setScene(scene);
primaryStage.setTitle(getClass().getName());
primaryStage.show();
}
}
