package jp.classmethod.sample.mp4parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.List;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.googlecode.mp4parser.authoring.tracks.TextTrackImpl;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;


public class MainActivity extends FragmentActivity implements LoaderCallbacks<Boolean> {

	private final MainActivity self = this;
	private ProgressDialog mProgressDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		findViewById(R.id.append).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				
				String root = Environment.getExternalStorageDirectory().toString();
				String audio = root + "/"+"audio_Capturing-190814-034638.422.m4a";
				String video = root + "/"+"game_capturing-190814-034638.378.mp4";
				String output = root + "/"+"ouput.mp4";
				Log.e("FILE", "audio:"+audio + " video:"+video+ " out:"+output);
				mux(video, audio, output);
			}
		});

		findViewById(R.id.crop).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				/*
				mProgressDialog = ProgressDialog.show(self, null, null);
				Bundle args = new Bundle();
				args.putInt("type", 1);
				getSupportLoaderManager().initLoader(0, args, self);
				*/
				Thread thread = new Thread(convert);
				thread.start();
			}
		});

		findViewById(R.id.sub_title).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mProgressDialog = ProgressDialog.show(self, null, null);
				Bundle args = new Bundle();
				args.putInt("type", 2);
				getSupportLoaderManager().initLoader(0, args, self);
			}
		});

	}

	@Override
	public Loader<Boolean> onCreateLoader(int id, Bundle args) {
		return new EditMovieTask(self, args.getInt("type"));
	}

	@Override
	public void onLoadFinished(Loader<Boolean> loader, Boolean succeed) {
		getSupportLoaderManager().destroyLoader(loader.getId());
		mProgressDialog.dismiss();
	}

	@Override
	public void onLoaderReset(Loader<Boolean> loader) {
	}

	public static class EditMovieTask extends AsyncTaskLoader<Boolean> {

		private int mType;

		public EditMovieTask(Context context, int type) {
			super(context);
			mType = type;
			forceLoad();
		}

		@Override
		public Boolean loadInBackground() {

			switch (mType) {
			case 0:
				return append();
			case 1:
				return crop();
			case 2:
				return subTitle();
			}

			return false;
		}

		private boolean append() {
			try {
				// 複数の動画を読み込み
				String f1 = Environment.getExternalStorageDirectory() + "/sample1.mp4";
				String f2 = Environment.getExternalStorageDirectory() + "/sample2.mp4";
				Movie[] inMovies = new Movie[]{
						MovieCreator.build(f1),
						MovieCreator.build(f2)};

				// 1つのファイルに結合
				List<Track> videoTracks = new LinkedList<Track>();
				List<Track> audioTracks = new LinkedList<Track>();
				for (Movie m : inMovies) {
					for (Track t : m.getTracks()) {
						if (t.getHandler().equals("soun")) {
							audioTracks.add(t);
						}
						if (t.getHandler().equals("vide")) {
							videoTracks.add(t);
						}
					}
				}
				Movie result = new Movie();
				if (audioTracks.size() > 0) {
					result.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
				}
				if (videoTracks.size() > 0) {
					result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
				}

				// 出力
				Container out = new DefaultMp4Builder().build(result);
				String outputFilePath = Environment.getExternalStorageDirectory() + "/output_append.mp4";
				FileOutputStream fos = new FileOutputStream(new File(outputFilePath));
				out.writeContainer(fos.getChannel());
				fos.close();
			} catch (Exception e) {
				return false;
			}

			return true;
		}

		private boolean crop() {
			try {
				// オリジナル動画を読み込み
				String filePath = Environment.getExternalStorageDirectory() + "/sample1.mp4";
				Movie originalMovie = MovieCreator.build(filePath);

				// 分割
				Track track = originalMovie.getTracks().get(0);
				Movie movie = new Movie();
				movie.addTrack(new AppendTrack(new CroppedTrack(track, 200, 400)));

				// 出力
				Container out = new DefaultMp4Builder().build(movie);
				String outputFilePath = Environment.getExternalStorageDirectory() + "/output_crop.mp4";
				FileOutputStream fos = new FileOutputStream(new File(outputFilePath));
				out.writeContainer(fos.getChannel());
				fos.close();
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}

		private boolean subTitle() {
			try {
				// オリジナル動画を読み込み
				String filePath = Environment.getExternalStorageDirectory() + "/sample1.mp4";
				Movie countVideo = MovieCreator.build(filePath);

				// SubTitleを追加
				TextTrackImpl subTitleEng = new TextTrackImpl();
				subTitleEng.getTrackMetaData().setLanguage("eng");

				subTitleEng.getSubs().add(new TextTrackImpl.Line(0, 1000, "Five"));
				subTitleEng.getSubs().add(new TextTrackImpl.Line(1000, 2000, "Four"));
				subTitleEng.getSubs().add(new TextTrackImpl.Line(2000, 3000, "Three"));
				subTitleEng.getSubs().add(new TextTrackImpl.Line(3000, 4000, "Two"));
				subTitleEng.getSubs().add(new TextTrackImpl.Line(4000, 5000, "one"));
				countVideo.addTrack(subTitleEng);

				// 出力
				Container container = new DefaultMp4Builder().build(countVideo);
				String outputFilePath = Environment.getExternalStorageDirectory() + "/output_subtitle.mp4";
				FileOutputStream fos = new FileOutputStream(outputFilePath);
				FileChannel channel = fos.getChannel();
				container.writeContainer(channel);
				fos.close();
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}
	}
	
	public boolean mux(String videoFile, String audioFile, String outputFile) {
		Movie video;
		try {
			video = new MovieCreator().build(videoFile);
		} catch (RuntimeException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		Movie audio;
		try {
			audio = new MovieCreator().build(audioFile);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (NullPointerException e) {
			e.printStackTrace();
			return false;
		}

		Track audioTrack = audio.getTracks().get(0);
		video.addTrack(audioTrack);

		Container out = new DefaultMp4Builder().build(video);

		FileOutputStream fos;
		try {
			fos = new FileOutputStream(outputFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		BufferedWritableFileByteChannel byteBufferByteChannel = new BufferedWritableFileByteChannel(fos);
		try {
			out.writeContainer(byteBufferByteChannel);
			byteBufferByteChannel.close();
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private static class BufferedWritableFileByteChannel implements WritableByteChannel {
		private static final int BUFFER_CAPACITY = 1000000;

		private boolean isOpen = true;
		private final OutputStream outputStream;
		private final ByteBuffer byteBuffer;
		private final byte[] rawBuffer = new byte[BUFFER_CAPACITY];

		private BufferedWritableFileByteChannel(OutputStream outputStream) {
			this.outputStream = outputStream;
			this.byteBuffer = ByteBuffer.wrap(rawBuffer);
		}

		@Override
		public int write(ByteBuffer inputBuffer) throws IOException {
			int inputBytes = inputBuffer.remaining();

			if (inputBytes > byteBuffer.remaining()) {
				dumpToFile();
				byteBuffer.clear();

				if (inputBytes > byteBuffer.remaining()) {
					throw new BufferOverflowException();
				}
			}

			byteBuffer.put(inputBuffer);

			return inputBytes;
		}

		@Override
		public boolean isOpen() {
			return isOpen;
		}

		@Override
		public void close() throws IOException {
			dumpToFile();
			isOpen = false;
		}
		private void dumpToFile() {
			try {
				outputStream.write(rawBuffer, 0, byteBuffer.position());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	public static final String AUDIO_RECORDING_FILE_NAME = "audio_Capturing-190814-034638.422.wav"; // Input PCM file
	public static final String COMPRESSED_AUDIO_FILE_NAME = "convertedmp4.m4a"; // Output MP4/M4A file
	public static final String COMPRESSED_AUDIO_FILE_MIME_TYPE = "audio/mp4a-latm";
	public static final int COMPRESSED_AUDIO_FILE_BIT_RATE = 64000; // 64kbps
	public static final int SAMPLING_RATE = 48000;
	public static final int BUFFER_SIZE = 48000;
	public static final int CODEC_TIMEOUT_IN_MS = 5000;
	String LOGTAG = "CONVERT AUDIO";
	Runnable convert = new Runnable() {
		@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
		@Override
		public void run() {
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
			try {
				String filePath = Environment.getExternalStorageDirectory().getPath() + "/" + AUDIO_RECORDING_FILE_NAME;
				File inputFile = new File(filePath);
				FileInputStream fis = new FileInputStream(inputFile);

				File outputFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + COMPRESSED_AUDIO_FILE_NAME);
				if (outputFile.exists()) outputFile.delete();

				MediaMuxer mux = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

				MediaFormat outputFormat = MediaFormat.createAudioFormat(COMPRESSED_AUDIO_FILE_MIME_TYPE,SAMPLING_RATE, 1);
				outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
				outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, COMPRESSED_AUDIO_FILE_BIT_RATE);
				outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

				MediaCodec codec = MediaCodec.createEncoderByType(COMPRESSED_AUDIO_FILE_MIME_TYPE);
				codec.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
				codec.start();

				ByteBuffer[] codecInputBuffers = codec.getInputBuffers(); // Note: Array of buffers
				ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

				MediaCodec.BufferInfo outBuffInfo = new MediaCodec.BufferInfo();
				byte[] tempBuffer = new byte[BUFFER_SIZE];
				boolean hasMoreData = true;
				double presentationTimeUs = 0;
				int audioTrackIdx = 0;
				int totalBytesRead = 0;
				int percentComplete = 0;
				do {
					int inputBufIndex = 0;
					while (inputBufIndex != -1 && hasMoreData) {
						inputBufIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_IN_MS);

						if (inputBufIndex >= 0) {
							ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
							dstBuf.clear();

							int bytesRead = fis.read(tempBuffer, 0, dstBuf.limit());
							Log.e("bytesRead","Readed "+bytesRead);
							if (bytesRead == -1) { // -1 implies EOS
								hasMoreData = false;
								codec.queueInputBuffer(inputBufIndex, 0, 0, (long) presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							} else {
								totalBytesRead += bytesRead;
								dstBuf.put(tempBuffer, 0, bytesRead);
								codec.queueInputBuffer(inputBufIndex, 0, bytesRead, (long) presentationTimeUs, 0);
								presentationTimeUs = 1000000l * (totalBytesRead / 2) / SAMPLING_RATE;
							}
						}
					}
					// Drain audio
					int outputBufIndex = 0;
					while (outputBufIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
						outputBufIndex = codec.dequeueOutputBuffer(outBuffInfo, CODEC_TIMEOUT_IN_MS);
						if (outputBufIndex >= 0) {
							ByteBuffer encodedData = codecOutputBuffers[outputBufIndex];
							encodedData.position(outBuffInfo.offset);
							encodedData.limit(outBuffInfo.offset + outBuffInfo.size);
							if ((outBuffInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 && outBuffInfo.size != 0) {
								codec.releaseOutputBuffer(outputBufIndex, false);
							}else{
								mux.writeSampleData(audioTrackIdx, codecOutputBuffers[outputBufIndex], outBuffInfo);
								codec.releaseOutputBuffer(outputBufIndex, false);
							}
						} else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
							outputFormat = codec.getOutputFormat();
							Log.v(LOGTAG, "Output format changed - " + outputFormat);
							audioTrackIdx = mux.addTrack(outputFormat);
							mux.start();
						} else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
							Log.e(LOGTAG, "Output buffers changed during encode!");
						} else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
							// NO OP
						} else {
							Log.e(LOGTAG, "Unknown return code from dequeueOutputBuffer - " + outputBufIndex);
						}
					}
					percentComplete = (int) Math.round(((float) totalBytesRead / (float) inputFile.length()) * 100.0);
					Log.v(LOGTAG, "Conversion % - " + percentComplete);
				} while (outBuffInfo.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM);
				fis.close();
				mux.stop();
				mux.release();
				Log.v(LOGTAG, "Compression done ...");
			} catch (FileNotFoundException e) {
				Log.e(LOGTAG, "File not found!", e);
			} catch (IOException e) {
				Log.e(LOGTAG, "IO exception!", e);
			}
			
			//mStop = false;
			// Notify UI thread...
		}
	};
	
	

}
