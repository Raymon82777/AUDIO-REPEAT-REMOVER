package audio.repeat.remover;

import be.tarsos.dsp.*;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.util.fft.FFT;

import java.util.*;

public class AudioRepeatRemover {
    public static void main(String[] args) {
        String inputFilePath = "input.wav"; // Path to input audio file
        String outputFilePath = "output.wav"; // Path to output audio file

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

    private static Set<Integer> detectRepeatedSegments(List<float[]> fingerprints) {
        Set<Integer> repeatedIndexes = new HashSet<>();
        // Simple comparison to find repeated fingerprints
        for (int i = 0; i < fingerprints.size(); i++) {
            for (int j = i + 1; j < fingerprints.size(); j++) {
                if (areFingerprintsSimilar(fingerprints.get(i), fingerprints.get(j))) {
                    repeatedIndexes.add(i);
                    repeatedIndexes.add(j);
                }
            }
        }
        return repeatedIndexes;
    }

    private static boolean areFingerprintsSimilar(float[] fp1, float[] fp2) {
        // Compute similarity (e.g., cosine similarity or Euclidean distance)
        double threshold = 0.8; // Adjust as needed
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < fp1.length; i++) {
            dotProduct += fp1[i] * fp2[i];
            norm1 += fp1[i] * fp1[i];
            norm2 += fp2[i] * fp2[i];
        }

        return (dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2))) > threshold;
    }

    private static void removeRepeatedSegments(String inputFilePath, String outputFilePath, Set<Integer> repeatedIndexes) {
        // Dummy implementation: Actual audio editing logic goes here
        System.out.println("Removing repeated segments...");
        // Use audio libraries like TarsosDSP or JAVE to edit the audio
    }
}
