import java.lang.invoke.MethodHandles;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.MathArrays;

import com.sun.javafx.util.Utils;

import jp.ac.kyoto_u.kuis.le4music.Le4MusicUtils;
import jp.ac.kyoto_u.kuis.le4music.LineChartWithSpectrogram;

import java.io.IOException;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.apache.commons.cli.ParseException;

public final class DictAiueo extends Application {

private static final Options options = new Options();
private static final String helpMessage =
MethodHandles.lookup().lookupClass().getName() + " [OPTIONS] <WAVFILE>";

static {
/* コ マ ン ド ラ イ ン オ プ シ ョ ン 定 義 */
options.addOption("h", "help", false, "Display this help and exit");
options.addOption("o", "outfile", true,
"Output image file (Default: " +
MethodHandles.lookup().lookupClass().getSimpleName() +
"." + Le4MusicUtils.outputImageExt + ")");
options.addOption("f", "frame", true,
"Duration of frame [seconds] (Default: " +
Le4MusicUtils.frameDuration + ")");
options.addOption("s", "shift", true,
"Duration of shift [seconds] (Default: frame/8)");
}

@Override public final void start(final Stage primaryStage)
throws IOException,
UnsupportedAudioFileException,
ParseException {
/* コ マ ン ド ラ イ ン 引 数 処 理 */
final String[] args = getParameters().getRaw().toArray(new String[0]);
final CommandLine cmd = new DefaultParser().parse(options, args);
if (cmd.hasOption("help")) {

new HelpFormatter().printHelp(helpMessage, options);
Platform.exit();
return;
}
final String[] pargs = cmd.getArgs();
if (pargs.length < 1) {
System.out.println("WAVFILE is not given.");
new HelpFormatter().printHelp(helpMessage, options);
Platform.exit();
return;
}
final File awavFile = new File(pargs[0]);
final File iwavFile = new File(pargs[1]);
final File uwavFile = new File(pargs[2]);
final File ewavFile = new File(pargs[3]);
final File owavFile = new File(pargs[4]);
final File wavFile = new File(pargs[5]);

/* W A V フ ァ イ ル 読 み 込 み */
final AudioInputStream stream = AudioSystem.getAudioInputStream(wavFile);
final double[] waveform = Le4MusicUtils.readWaveformMonaural(stream);
final AudioFormat format = stream.getFormat();
final double sampleRate = format.getSampleRate();
final double nyquist = sampleRate * 0.5;

double[] air = new double[3200];
double[] zerowaveform = new double[waveform.length + air.length];
System.arraycopy(waveform, 0, zerowaveform, 0, waveform.length);
System.arraycopy(air, 0, zerowaveform, waveform.length, air.length);

stream.close();

/* 窓 関 数 と F F T の サ ン プ ル 数 */
final double frameDuration =
Optional.ofNullable(cmd.getOptionValue("frame"))
.map(Double::parseDouble)
.orElse(Le4MusicUtils.frameDuration);
final int frameSize = (int)Math.round(frameDuration * sampleRate);
final int fftSize = 1 << Le4MusicUtils.nextPow2(frameSize);
final int fftSize2 = (fftSize >> 1) + 1;

/* シ フ ト の サ ン プ ル 数 */
final double shiftDuration =
Optional.ofNullable(cmd.getOptionValue("shift"))
.map(Double::parseDouble)
.orElse(Le4MusicUtils.frameDuration / 8);
final int shiftSize = (int)Math.round(shiftDuration * sampleRate);

/* 窓 関 数 を 求 め ， そ れ を 正 規 化 す る */
final double[] window = MathArrays.normalizeArray(
Arrays.copyOf(Le4MusicUtils.hanning(frameSize), fftSize), 1.0
);

/* 短 時 間 フ ー リ エ 変 換 本 体 */
final Stream<Complex[]> spectrogram1 =
Le4MusicUtils.sliding(waveform, window, shiftSize)
.map(frame -> Le4MusicUtils.rfft(frame));

/* 複 素 ス ペ ク ト ロ グ ラ ム を 対 数 振 幅 ス ペ ク ト ロ グ ラ ム に */
final double[][] specLog1 =
spectrogram1.map(sp -> Arrays.stream(sp)
.mapToDouble(c -> 20.0 * Math.log10(c.abs()))
.toArray())
.toArray(n -> new double[n][]);

/* 参 考 ： フ レ ー ム 数 と 各 フ レ ー ム 先 頭 位 置 の 時 刻 */
final double[] times =
IntStream.range(0, specLog1.length)
.mapToDouble(i -> i * shiftDuration)
.toArray();

/* 参 考 ： 各 フ ー リ エ 変 換 係 数 に 対 応 す る 周 波 数 */
final double[] freqs =
IntStream.range(0, fftSize2)
.mapToDouble(i -> i * sampleRate / fftSize)
.toArray();

for(int i = 0; i < fftSize2;i++) {
	System.out.print(i + " " + freqs[i] + "\n");
}

final Stream<Complex[]> spectrogram2 =
Le4MusicUtils.sliding(waveform, window, shiftSize)
.map(frame -> Le4MusicUtils.rfft(frame));

/* 複 素 ス ペ ク ト ロ グ ラ ム を 対 数 振 幅 ス ペ ク ト ロ グ ラ ム に */
final double[][] specLog2 =
spectrogram2.map(sp -> Arrays.stream(sp)
.mapToDouble(c -> Math.log10(c.abs()))
.toArray())
.toArray(n -> new double[n][]);

/* 各スペクトラムをケプトラムに変換 */
double[][] cepstrums = new double[specLog2.length][];
for(int i = 0; i < specLog2.length; i++) {
	double[] s = Arrays.copyOfRange(specLog2[i],0,specLog2[i].length-1);
	Complex[] cepstrum = Le4MusicUtils.fft(s);
	cepstrums[i] = Arrays.stream(cepstrum)
			.mapToDouble(c -> c.getReal())
			.toArray();
}

int dicter = 13;

/* 各音叉の学習を行う */
double[][] studyData = new double[10][cepstrums[0].length];
for(int i=0;i<5;i++) {
	AudioInputStream studystream;
	if(i==0) {
		studystream = AudioSystem.getAudioInputStream(awavFile);
	}
	else if(i==1){
		studystream = AudioSystem.getAudioInputStream(iwavFile);
	}
	else if(i==2){
		studystream = AudioSystem.getAudioInputStream(uwavFile);
	}
	else if(i==3){
		studystream = AudioSystem.getAudioInputStream(ewavFile);
	}
	else{
		studystream = AudioSystem.getAudioInputStream(owavFile);
	}
	double[] studywaveform = Le4MusicUtils.readWaveformMonaural(studystream);
	Stream<Complex[]> spectrogram3 = Le4MusicUtils.sliding(studywaveform, window, shiftSize).map(frame -> Le4MusicUtils.rfft(frame));
	double[][] specLog3 = spectrogram3.map(sp -> Arrays.stream(sp).mapToDouble(c -> Math.log10(c.abs())).toArray()).toArray(n -> new double[n][]);
	double[][] studycepstrums = new double[specLog3.length][];
	
	/* ケプストラムを作成 */
	for(int j = 0; j < specLog3.length; j++) {
		double[] s = Arrays.copyOfRange(specLog3[j],0,specLog3[j].length-1);
		Complex[] cepstrum = Le4MusicUtils.fft(s);
		studycepstrums[j] = Arrays.stream(cepstrum).mapToDouble(c -> c.getReal()).toArray();
	}
	/* μを求める */
	for(int j=0;j<studycepstrums.length;j++){
		double ans = 0;
		for(int k=0;k<dicter;k++){
			ans += studycepstrums[k][j];
		}
		studyData[i*2][j] = ans / studycepstrums.length;
	}
	/* σを求める */
	for(int j=0;j<studycepstrums.length;j++){
		double ans = 0;
		for(int k=0;k<dicter;k++){
			ans += Math.pow((studycepstrums[k][j]-studyData[i*2][j]),2);
		}
		studyData[i*2+1][j] = ans / studycepstrums.length;
	}
}

/* ゼロ交差数を求める */
final double durationSpec = frameDuration / (cepstrums[0].length - 1);

int[] zerocross = new int[shiftSize];
for(int i = 0; i < shiftSize; i++) {
	int count = 0;
	for(int j = 0; j < frameSize; j++) {
		int front = i * shiftSize + j;
		int back = i * shiftSize + j + 1;
		if(zerowaveform[front] * zerowaveform[back] <= 0 && Math.abs(zerowaveform[front] - zerowaveform[back]) > 0.001){
			count += 1;
		}
	}
	if(count > 30) {
		zerocross[i] = count;
	}
}

/* 基本周波数を求める */
double[] fundFreqs = new double[cepstrums.length];

for(int i = 0; i < cepstrums.length; i++) {
	double fundFreq = 50;
	int ans = 0;
	for (int j = 10; j < 200; j++) {

		
		if(fundFreq < cepstrums[i][j]) {
			fundFreq = cepstrums[i][j];
			ans = j;
		}
	}
	if ((ans < 50 || 1000 < ans) || zerocross[i] == 0){
		fundFreqs[i] = 0;
	}
	else {
		fundFreqs[i] = 1 / (ans * durationSpec);
	}
}

/*　学習結果の適応　*/
double[] dict = new double[cepstrums.length];
for(int i = 0; i < cepstrums.length; i++) {
	double ans_a = 0;
	double ans_i = 0;
	double ans_u = 0;
	double ans_e = 0;
	double ans_o = 0;
	for(int j = 0; j < dicter; j++) {
		ans_a += Math.log10(studyData[1][j]) + Math.pow(cepstrums[i][j]-studyData[0][j],2) / (2 * Math.pow(studyData[1][j],2));
		ans_i += Math.log10(studyData[3][j]) + Math.pow(cepstrums[i][j]-studyData[2][j],2) / (2 * Math.pow(studyData[3][j],2));
		ans_u += Math.log10(studyData[5][j]) + Math.pow(cepstrums[i][j]-studyData[4][j],2) / (2 * Math.pow(studyData[5][j],2));
		ans_e += Math.log10(studyData[7][j]) + Math.pow(cepstrums[i][j]-studyData[6][j],2) / (2 * Math.pow(studyData[7][j],2));
		ans_o += Math.log10(studyData[9][j]) + Math.pow(cepstrums[i][j]-studyData[8][j],2) / (2 * Math.pow(studyData[9][j],2));
	}
	int ans = 0;
	
	System.out.print( Math.round(i * shiftDuration) + "  " + " ans_a " + ans_a +" ans_i " + ans_i +" ans_u " + ans_u +" ans_e " + ans_e +" ans_o " + ans_o + "\n");
	if(zerocross[i] == 0) {
		ans = 0;
	}
	else if(ans_a>ans_i && ans_a>ans_u && ans_a>ans_e && ans_a>ans_o) {
		ans = 100;
	}
	else if(ans_i>ans_a && ans_i>ans_u && ans_i>ans_e && ans_i>ans_o) {
		ans = 200;
	}
	else if(ans_u>ans_a && ans_u>ans_i && ans_u>ans_e && ans_u>ans_o) {
		ans = 300;
	}
	else if(ans_e>ans_a && ans_e>ans_i && ans_e>ans_u && ans_e>ans_o) {
		ans = 400;
	}
	else if(ans_o>ans_a && ans_o>ans_i && ans_o>ans_u && ans_o>ans_e) {
		ans = 500;
	}
	dict[i] = ans;
}

final ObservableList<XYChart.Data<Number, Number>> data =
IntStream.range(0,dict.length)
.mapToObj(i -> new XYChart.Data<Number, Number>(i* shiftDuration,dict[i]))
.collect(Collectors.toCollection(FXCollections::observableArrayList));

final XYChart.Series<Number, Number> series =
new XYChart.Series<>("cepstrum", data);

/* X 軸 を 作 成 */
final double duration = (specLog1.length - 1) * shiftDuration;
final NumberAxis xAxis = new NumberAxis(
/* axisLabel = */ "Time (seconds)",
/* lowerBound = */ 0.0,
/* upperBound = */ duration,
/* tickUnit = */ 0.5
);
xAxis.setAnimated(false);

/* Y 軸 を 作 成 */
final NumberAxis yAxis = new NumberAxis(
/* axisLabel = */ "Frequency (Hz)",
/* lowerBound = */ 0.0,
/* upperBound = */ 500,
/* tickUnit = */ 100
);
yAxis.setAnimated(false);

/* チ ャ ー ト を 作 成 */
final LineChartWithSpectrogram<Number, Number> chart =
new LineChartWithSpectrogram<>(xAxis, yAxis);
chart.getData().add(series);
chart.setParameters(specLog1.length, fftSize2, nyquist);
chart.setTitle("Spectrogram");
Arrays.stream(specLog1).forEach(chart::addSpecLog);
chart.setCreateSymbols(true);
chart.setLegendVisible(false);

/* グ ラ フ 描 画 */
final Scene scene = new Scene(chart, 800, 600);

/* ウ イ ン ド ウ 表 示 */
primaryStage.setScene(scene);
primaryStage.setTitle(getClass().getName());
primaryStage.show();

}
}
