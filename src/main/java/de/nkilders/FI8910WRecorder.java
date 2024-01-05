package de.nkilders;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.scale.AWTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FI8910WRecorder {
	private static final Logger LOGGER = LoggerFactory.getLogger(FI8910WRecorder.class);

	private static final String           VIDEOS_DIR_NAME = "./videos";
	private static final SimpleDateFormat SDF             = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");

	private final byte[]     lastByte    = new byte[1];
	private final byte[]     currentByte = new byte[1];
	private final List<Byte> frameBuffer = new ArrayList<>();
	private final long       videoLength;
	private final URL        streamUrl;

	private boolean             recording = false;
	private boolean             inFrame   = false;
	private BufferedInputStream videoStream;
	private String              recordingFileName;
	private long                startTime;
	private SequenceEncoder     encoder;

	/**
	 * @param host        hostname or IP address of the camera
	 * @param user        username to log in to the camera
	 * @param password    password to log in to the camera
	 * @param videoLength maximum length of the created video files in seconds
	 *
	 * @throws FI8910WRecorderException if something goes wrong
	 */
	public FI8910WRecorder(String host, String user, String password, long videoLength) throws FI8910WRecorderException {
		this.streamUrl   = buildStreamUrl(host, user, password);
		this.videoLength = videoLength;
	}

	/**
	 * @param host     hostname or IP address of the camera
	 * @param user     username to log in to the camera
	 * @param password password to log in to the camera
	 *
	 * @throws FI8910WRecorderException if something goes wrong
	 */
	public FI8910WRecorder(String host, String user, String password) throws FI8910WRecorderException {
		this(host, user, password, 15);
	}

	public void start() throws FI8910WRecorderException {
		if (recording) {
			return;
		}

		recording = true;

		openVideoStream();
		setupSequenceEncoder();

		new Thread(runnable).start();
	}

	public void stop() {
		recording = false;
	}

	Runnable runnable = () -> {
		try {
			while (recording) {
				readFrame();

				if (videoMaxLengthReached()) {
					saveVideo();
					setupSequenceEncoder();
				}
			}

			saveVideo();
			closeVideoStream();
		} catch (FI8910WRecorderException ex) {
			LOGGER.error("Error while recording", ex);
		}
	};

	private void readFrame() throws FI8910WRecorderException {
		readNextByte();

		if (!inFrame && startOfImage()) {
			inFrame = true;

			frameBuffer.clear();
			frameBuffer.add(lastByte[0]);
		}

		if (!inFrame) {
			return;
		}

		frameBuffer.add(currentByte[0]);

		if (endOfImage()) {
			encodeFrame();
			inFrame = false;
		}
	}

	private boolean startOfImage() {
		return lastByte[0] == -1 && currentByte[0] == -40;
	}

	private boolean endOfImage() {
		return lastByte[0] == -1 && currentByte[0] == -39;
	}

	private URL buildStreamUrl(String host, String user, String password) throws FI8910WRecorderException {
		user     = URLEncoder.encode(user, StandardCharsets.UTF_8);
		password = URLEncoder.encode(password, StandardCharsets.UTF_8);

		try {
			return new URL(String.format("http://%s/videostream.cgi?user=%s&pwd=%s", host, user, password));
		} catch (MalformedURLException ex) {
			throw new FI8910WRecorderException("Could not build stream URL", ex);
		}
	}

	private boolean videoMaxLengthReached() {
		return (System.currentTimeMillis() - startTime) >= videoLength * 1000;
	}

	private void createVideosFolder() {
		File dir = new File(VIDEOS_DIR_NAME);

		if (!dir.exists()) {
			//noinspection ResultOfMethodCallIgnored
			dir.mkdir();
		}
	}

	private void openVideoStream() throws FI8910WRecorderException {
		if (videoStream != null) {
			return;
		}

		try {
			InputStream inputStream = streamUrl.openStream();
			videoStream = new BufferedInputStream(inputStream);
		} catch (IOException ex) {
			throw new FI8910WRecorderException("Could not open video stream", ex);
		}
	}

	private void closeVideoStream() throws FI8910WRecorderException {
		if (videoStream == null) {
			return;
		}

		try {
			videoStream.close();
		} catch (IOException ex) {
			throw new FI8910WRecorderException("Could not close video stream", ex);
		}
	}

	private void readNextByte() throws FI8910WRecorderException {
		lastByte[0] = currentByte[0];

		try {
			videoStream.read(currentByte);
		} catch (IOException ex) {
			throw new FI8910WRecorderException("Could not read from video stream", ex);
		}
	}

	private void setupSequenceEncoder() throws FI8910WRecorderException {
		createVideosFolder();

		startTime         = System.currentTimeMillis();
		recordingFileName = String.format("%s.mp4", SDF.format(new Date()));

		try {
			encoder = SequenceEncoder.createSequenceEncoder(
				new File(String.format("%s/%s", VIDEOS_DIR_NAME, recordingFileName)),
				15
			);
		} catch (IOException ex) {
			throw new FI8910WRecorderException("Could not create sequence encoder", ex);
		}
	}

	private void encodeFrame() throws FI8910WRecorderException {
		byte[] bytes = frameBufferAsArray();

		frameBuffer.clear();

		try {
			BufferedImage frame = ImageIO.read(new ByteArrayInputStream(bytes));
			encoder.encodeNativeFrame(AWTUtil.fromBufferedImage(frame, ColorSpace.RGB));
		} catch (IOException ex) {
			throw new FI8910WRecorderException("Could not encode frame", ex);
		}
	}

	private void saveVideo() throws FI8910WRecorderException {
		try {
			encoder.finish();
		} catch (IOException ex) {
			throw new FI8910WRecorderException("Could not save recording", ex);
		}

		LOGGER.info("Successfully saved recording {}", recordingFileName);
	}

	private byte[] frameBufferAsArray() {
		byte[] arr = new byte[frameBuffer.size()];

		for (int i = 0; i < frameBuffer.size(); i++) {
			arr[i] = frameBuffer.get(i);
		}

		return arr;
	}
}
