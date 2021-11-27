package de.nkilders;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FI8910WRecorder {
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
    private static final long VIDEO_LENGTH = 1000 * 60 * 15;
    private static final String OUTPUT_DIR = "./videos";

    private BufferedInputStream inputStream;
    private String recordingFileName;
    private SequenceEncoder encoder;
    private long startTime;

    private byte[] lastByte = new byte[1];
    private byte[] currentByte = new byte[1];

    List<Byte> buffer = new ArrayList<>();

    public FI8910WRecorder(String host, String user, String password) {
        startTime = System.currentTimeMillis();

        user = urlEncode(user);
        password = urlEncode(password);

        try {
            URL url = new URL(String.format("http://%s/videostream.cgi?user=%s&pwd=%s", host, user, password));
            inputStream = new BufferedInputStream(url.openStream());
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        setupSequenceEncoder();

        while (true) {
            readStream(inputStream);

            // JPEG Start of Image (SOI)
            if (lastByte[0] == -1 && currentByte[0] == -40) {
                buffer.clear();
                buffer.add(lastByte[0]);
            }

            buffer.add(currentByte[0]);

            // JPEG End of Image (EOI)
            if (lastByte[0] == -1 && currentByte[0] == -39) {
                encodeFrame(buffer);

                if (System.currentTimeMillis() - startTime >= VIDEO_LENGTH) {
                    saveVideoAndRestartEncoder();
                }
            }
        }
    }

    private String urlEncode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    private void readStream(BufferedInputStream inputStream) {
        lastByte[0] = currentByte[0];

        try {
            inputStream.read(currentByte);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void setupSequenceEncoder() {
        recordingFileName = SDF.format(new Date()) + ".mp4";

        try {
            encoder = SequenceEncoder.createSequenceEncoder(
                    new File(String.format("%s/%s", OUTPUT_DIR, recordingFileName)),
                    15
            );
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void encodeFrame(List<Byte> buffer) {
        byte[] bytes = listToArray(buffer);

        try {
            BufferedImage frame = ImageIO.read(new ByteArrayInputStream(bytes));
            encoder.encodeNativeFrame(AWTUtil.fromBufferedImage(frame, ColorSpace.RGB));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void saveVideoAndRestartEncoder() {
        try {
            encoder.finish();
            System.out.println("Successfully saved recording " + recordingFileName);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        startTime = System.currentTimeMillis();
        setupSequenceEncoder();
    }

    private byte[] listToArray(List<Byte> list) {
        byte[] arr = new byte[list.size()];
        int i = 0;

        for (byte b : list) {
            arr[i++] = b;
        }

        return arr;
    }

}