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

public final class PlotSHS extends Application {

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
final File wavFile = new File(pargs[0]);

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
.mapToDouble(c -> c.abs())
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

/* 33番目から84番目までのノードの周波数の列 */
double[] frequencies = new double[49];
frequencies[0] = 65.4;
frequencies[1] = 69.3;
frequencies[2] = 73.4;
frequencies[3] = 77.8;
frequencies[4] = 82.4;
frequencies[5] = 87.3;
frequencies[6] = 92.5;
frequencies[7] = 98.0;
frequencies[8] = 103.8;
frequencies[9] = 110.0;
frequencies[10] = 116.5;
frequencies[11] = 123.4;
frequencies[48] = 1046.5;
for(int i=1;i<4;i++) {
	for(int j=0;j<12;j++) {
		frequencies[12*i+j] = frequencies[12*(i-1)+j]*2;
	}
}

/* 33番目から117番目までのノードの周波数の列 */
int nodenums[] = new int[49];
int p = 0, q = 0;
while(q < 49 && p < 400) {
	if(freqs[p] > frequencies[q]) {
		nodenums[q] = p;
		q++;
		p++;
	}
	p++;
}

/*
/* ゼロ交差数を求める */
/*
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
*/
/* 基本周波数を求める */
/*
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
*/
/*
int[] answer = new int[specLog1.length];

for(int i=0;i<specLog1.length;i++) {
double[] dict = new double[12];
dict[0] = specLog1[i][17] + specLog1[i][33] + specLog1[i][67] + specLog1[i][134] + specLog1[i][268] + specLog1[i][536] + specLog1[i][1072]; // C
dict[1] = specLog1[i][18] + specLog1[i][36] + specLog1[i][71] + specLog1[i][142] + specLog1[i][284] + specLog1[i][568] + specLog1[i][1136]; // C#
dict[2] = specLog1[i][19] + specLog1[i][38] + specLog1[i][75] + specLog1[i][150] + specLog1[i][301] + specLog1[i][601] + specLog1[i][1202]; // D
dict[3] = specLog1[i][20] + specLog1[i][40] + specLog1[i][80] + specLog1[i][159] + specLog1[i][318] + specLog1[i][637] + specLog1[i][1274]; // D#
dict[4] = specLog1[i][21] + specLog1[i][42] + specLog1[i][84] + specLog1[i][169] + specLog1[i][338] + specLog1[i][675] + specLog1[i][1350]; // E
dict[5] = specLog1[i][22] + specLog1[i][45] + specLog1[i][89] + specLog1[i][179] + specLog1[i][358] + specLog1[i][715] + specLog1[i][1430]; // F
dict[6] = specLog1[i][24] + specLog1[i][47] + specLog1[i][95] + specLog1[i][189] + specLog1[i][379] + specLog1[i][758] + specLog1[i][1516]; // F#
dict[7] = specLog1[i][25] + specLog1[i][50] + specLog1[i][100] + specLog1[i][200] + specLog1[i][401] + specLog1[i][803] + specLog1[i][1606]; // G
dict[8] = specLog1[i][27] + specLog1[i][53] + specLog1[i][106] + specLog1[i][212] + specLog1[i][425] + specLog1[i][850] + specLog1[i][1700]; // G#
dict[9] = specLog1[i][28] + specLog1[i][56] + specLog1[i][113] + specLog1[i][225] + specLog1[i][451] + specLog1[i][901] + specLog1[i][1802]; // A
dict[10] = specLog1[i][15] + specLog1[i][30] + specLog1[i][60] + specLog1[i][119] + specLog1[i][239] + specLog1[i][477] + specLog1[i][955]; // A#
dict[11] = specLog1[i][16]+ specLog1[i][32] + specLog1[i][63] + specLog1[i][126] + specLog1[i][253] + specLog1[i][506] + specLog1[i][1011]; // B

double[] chords = new double[24];
chords[0] = dict[0] + 0.5 * dict[4] + 0.8 * dict[7]; // CMajor
chords[1] = dict[0] + 0.5 * dict[3] + 0.8 * dict[7]; // CMinor
chords[2] = dict[1] + 0.5 * dict[5] + 0.8 * dict[8]; // C#Major
chords[3] = dict[1] + 0.5 * dict[4] + 0.8 * dict[8]; // C#Minor
chords[4] = dict[2] + 0.5 * dict[6] + 0.8 * dict[9]; // DMinor
chords[5] = dict[2] + 0.5 * dict[5] + 0.8 * dict[9]; // DMinor
chords[6] = dict[3] + 0.5 * dict[7] + 0.8 * dict[10]; // D#Minor
chords[7] = dict[3] + 0.5 * dict[6] + 0.8 * dict[10]; // D#Minor
chords[8] = dict[4] + 0.5 * dict[8] + 0.8 * dict[11]; // EMinor
chords[9] = dict[4] + 0.5 * dict[7] + 0.8 * dict[11]; // EMinor
chords[10] = dict[5] + 0.5 * dict[9] + 0.8 * dict[0]; // FMinor
chords[11] = dict[5] + 0.5 * dict[8] + 0.8 * dict[0]; // FMinor
chords[12] = dict[6] + 0.5 * dict[10] + 0.8 * dict[1]; // F#Minor
chords[13] = dict[6] + 0.5 * dict[9] + 0.8 * dict[1]; // F#Minor
chords[14] = dict[7] + 0.5 * dict[11] + 0.8 * dict[2]; // GMinor
chords[15] = dict[7] + 0.5 * dict[10] + 0.8 * dict[2]; // GMinor
chords[16] = dict[8] + 0.5 * dict[0] + 0.8 * dict[3]; // G#Minor
chords[17] = dict[8] + 0.5 * dict[11] + 0.8 * dict[3]; // G#Minor
chords[18] = dict[9] + 0.5 * dict[1] + 0.8 * dict[4]; // AMinor
chords[19] = dict[9] + 0.5 * dict[0] + 0.8 * dict[4]; // AMinor
chords[20] = dict[10] + 0.5 * dict[2] + 0.8 * dict[5]; // A#Minor
chords[21] = dict[10] + 0.5 * dict[1] + 0.8 * dict[5]; // A#Minor
chords[22] = dict[11] + 0.5 * dict[3] + 0.8 * dict[6]; // BMinor
chords[23] = dict[11] + 0.5 * dict[2] + 0.8 * dict[6]; // BMinor

int max = 0;
double max_val = chords[0];
for(int j=0;j<24;j++) {
	if(max_val < chords[j]) {
		max_val = chords[j];
		max = j;
	}

}
answer[i] = max; 
}
*/
double[] basicfqs = new double[specLog1.length];

for(int k = 0;k< specLog1.length;k++) {
	
/* SSHを使って目的の基本周波数を推定する */
/* 候補を36から60にする */
	
double[] candidates = new double[25];
for(int i=0;i<=24;i++) {
	double f = frequencies[i];
	double each_value = 0;
	int r=1;
	while(r<=3) {
		double fp = f * r;
		int first_num = 0;
		for(int j=0;j<49;j++) {
			if(frequencies[j] >= fp) { 
				first_num = j;
				break;
			}
		}
		each_value += specLog1[k][nodenums[first_num]] / r;
		r++;
	}
	candidates[i] = each_value;
	System.out.print( i + " "+ each_value + " ");
}
System.out.print("\n");
int ans = 0;
double ans_val = 0;
for(int i=0;i<25;i++) {
	if (ans_val <= candidates[i]) {
		ans = i;
		ans_val = candidates[i];
	}
}
System.out.print(ans+"	");
basicfqs[k] = frequencies[ans];
System.out.print(basicfqs[k]+"\n");
}

final ObservableList<XYChart.Data<Number, Number>> data =
IntStream.range(0,specLog1.length)
.mapToObj(i -> new XYChart.Data<Number, Number>(i*shiftDuration,basicfqs[i]))
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
/* upperBound = */ 270,
/* tickUnit = */ 10
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
