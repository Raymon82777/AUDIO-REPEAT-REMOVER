package audio.repeat.remover;

import be.tarsos.dsp.*;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.io.jvm.WaveformWriter;
import be.tarsos.dsp.util.fft.FFT;

import javax.sound.sampled.AudioFormat;
import javax.swing.*;
import java.io.File;
import java.util.*;

public class AudioRepeatRemover {

    private static final AudioFormat format = new AudioFormat(44100, 16, 1, true, false);

    public static void main(String[] args) {
        // Use JFileChooser to select multiple input files
        JFileChooser fileChooser = new JFileChooser("tracks/inputs");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);
        int result = fileChooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) {
            System.out.println("No files selected. Exiting.");
            return;
        }
        File[] selectedFiles = fileChooser.getSelectedFiles();

        for (File selectedFile : selectedFiles) {
            String inputFilePath = selectedFile.getAbsolutePath();
            
            // Generate output file path
            String outputFileName = selectedFile.getName().replaceFirst("[.][^.]+$", "") + "_repremoved.wav";
            String outputFilePath = new File(selectedFile.getParentFile().getParent(), "outputs/" + outputFileName).getAbsolutePath();

            try {
                // Step 1: Load the audio file
                AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(inputFilePath, 44100, 1024, 512);

                // Step 2: Analyze the audio and extract fingerprints
                List<float[]> fingerprints = new ArrayList<>();
                dispatcher.addAudioProcessor(new AudioProcessor() {
                    private FFT fft = new FFT(1024);

                    @Override
                    public boolean process(AudioEvent audioEvent) {
                        float[] spectrum = new float[fft.size() / 2]; // Correct length for the spectrum array
                        fft.forwardTransform(audioEvent.getFloatBuffer());
                        fft.modulus(audioEvent.getFloatBuffer(), spectrum);
                        fingerprints.add(spectrum);
                        return true;
                    }

                    @Override
                    public void processingFinished() {
                        // No-op
                    }
                });

                dispatcher.run();

                // Step 3: Detect repeated segments
                Set<Integer> repeatedIndexes = detectRepeatedSegments(fingerprints);

                // Step 4: Remove the repeated segments from the audio
                removeRepeatedSegments(inputFilePath, outputFilePath, repeatedIndexes);

                System.out.println("Processing complete. Output saved to " + outputFilePath);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Set<Integer> detectRepeatedSegments(List<float[]> fingerprints) {
        Set<Integer> repeatedIndexes = new HashSet<>();
        int windowSize = 5; // Number of consecutive frames to compare

        for (int i = 0; i < fingerprints.size() - windowSize; i++) {
            for (int j = i + windowSize; j < fingerprints.size() - windowSize; j++) {
                boolean isRepeated = true;
                for (int k = 0; k < windowSize; k++) {
                    if (!areFingerprintsSimilar(fingerprints.get(i + k), fingerprints.get(j + k))) {
                        isRepeated = false;
                        break;
                    }
                }
                if (isRepeated) {
                    for (int k = 0; k < windowSize; k++) {
                        repeatedIndexes.add(i + k);
                        repeatedIndexes.add(j + k);
                    }
                }
            }
        }
        return repeatedIndexes;
    }

    private static boolean areFingerprintsSimilar(float[] fp1, float[] fp2) {
        // Use Dynamic Time Warping (DTW) to measure similarity
        double threshold = 0.8; // Adjust as needed
        double dtwDistance = computeDTWDistance(fp1, fp2);

        return dtwDistance < threshold;
    }

    private static double computeDTWDistance(float[] fp1, float[] fp2) {
        int n = fp1.length;
        int m = fp2.length;
        double[][] dtw = new double[n][m];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                dtw[i][j] = Double.POSITIVE_INFINITY;
            }
        }
        dtw[0][0] = 0;

        for (int i = 1; i < n; i++) {
            for (int j = 1; j < m; j++) {
                double cost = Math.abs(fp1[i] - fp2[j]);
                dtw[i][j] = cost + Math.min(Math.min(dtw[i - 1][j], dtw[i][j - 1]), dtw[i - 1][j - 1]);
            }
        }

        return dtw[n - 1][m - 1];
    }

    private static void removeRepeatedSegments(String inputFilePath, String outputFilePath, Set<Integer> repeatedIndexes) {
        try {
            AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(inputFilePath, 44100, 1024, 512);
            WaveformWriter writer = new WaveformWriter(format, outputFilePath);

            dispatcher.addAudioProcessor(new AudioProcessor() {
                private int frameCount = 0;

                @Override
                public boolean process(AudioEvent audioEvent) {
                    if (!repeatedIndexes.contains(frameCount)) {
                        writer.process(audioEvent);
                    }
                    frameCount++;
                    return true;
                }

                @Override
                public void processingFinished() {
                    writer.processingFinished();
                }
            });

            dispatcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
