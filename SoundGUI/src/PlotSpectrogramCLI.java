import java.lang.invoke.MethodHandles;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import java.util.stream.IntStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.MathArrays;

import jp.ac.kyoto_u.kuis.le4music.Le4MusicUtils;
import jp.ac.kyoto_u.kuis.le4music.LineChartWithSpectrogram;

import java.io.IOException;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.apache.commons.cli.ParseException;

public final class PlotSpectrogramCLI extends Application {

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
final Stream<Complex[]> spectrogram =
Le4MusicUtils.sliding(waveform, window, shiftSize)
.map(frame -> Le4MusicUtils.rfft(frame));

/* 複 素 ス ペ ク ト ロ グ ラ ム を 対 数 振 幅 ス ペ ク ト ロ グ ラ ム に */
final double[][] specLog =
spectrogram.map(sp -> Arrays.stream(sp)
.mapToDouble(c -> 20.0 * Math.log10(c.abs()))
.toArray())
.toArray(n -> new double[n][]);

/* 参 考 ： フ レ ー ム 数 と 各 フ レ ー ム 先 頭 位 置 の 時 刻 */
final double[] times =
IntStream.range(0, specLog.length)

.mapToDouble(i -> i * shiftDuration)
.toArray();

/* 参 考 ： 各 フ ー リ エ 変 換 係 数 に 対 応 す る 周 波 数 */
final double[] freqs =
IntStream.range(0, fftSize2)
.mapToDouble(i -> i * sampleRate / fftSize)
.toArray();

System.out.print("frame duration " + fftSize + "\n");

/* X 軸 を 作 成 */
final double duration = (specLog.length - 1) * shiftDuration;
final NumberAxis xAxis = new NumberAxis(
/* axisLabel = */ "Time (seconds)",
/* lowerBound = */ 0.0,
/* upperBound = */ duration,
/* tickUnit = */ Le4MusicUtils.autoTickUnit(duration)
);
xAxis.setAnimated(false);

/* Y 軸 を 作 成 */
final NumberAxis yAxis = new NumberAxis(
/* axisLabel = */ "Frequency (Hz)",
/* lowerBound = */ 0.0,
/* upperBound = */ nyquist,
/* tickUnit = */ Le4MusicUtils.autoTickUnit(nyquist)
);
yAxis.setAnimated(false);

/* チ ャ ー ト を 作 成 */
final LineChartWithSpectrogram<Number, Number> chart =
new LineChartWithSpectrogram<>(xAxis, yAxis);
chart.setParameters(specLog.length, fftSize2, nyquist);
chart.setTitle("Spectrogram");
Arrays.stream(specLog).forEach(chart::addSpecLog);
chart.setCreateSymbols(false);
chart.setLegendVisible(false);

/* グ ラ フ 描 画 */
final Scene scene = new Scene(chart, 800, 600);
scene.getStylesheets().add("src/le4music.css");

/* ウ イ ン ド ウ 表 示 */
primaryStage.setScene(scene);
primaryStage.setTitle(getClass().getName());
primaryStage.show();

/* チ ャ ー ト を 画 像 フ ァ イ ル へ 出 力 */
Platform.runLater(() -> {
final String[] name_ext = Le4MusicUtils.getFilenameWithImageExt(
Optional.ofNullable(cmd.getOptionValue("outfile")),
getClass().getSimpleName()
);
final WritableImage image = scene.snapshot(null);
try {
ImageIO.write(SwingFXUtils.fromFXImage(image, null),
name_ext[1], new File(name_ext[0] + "." + name_ext[1]));
} catch (IOException e) { e.printStackTrace(); }

});
}

}
