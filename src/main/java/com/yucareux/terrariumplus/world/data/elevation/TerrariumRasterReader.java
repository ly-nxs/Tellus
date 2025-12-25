package com.yucareux.terrariumplus.world.data.elevation;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.tukaani.xz.SingleXZInputStream;

final class TerrariumRasterReader {
	private static final byte[] SIGNATURE = "TERRARIUM/RASTER".getBytes(StandardCharsets.US_ASCII);
	private static final int FORMAT_SHORT = 2;

	private TerrariumRasterReader() {
	}

	static ShortRaster readShortRaster(InputStream input) throws IOException {
		DataInputStream dataIn = new DataInputStream(input);
		byte[] signature = new byte[SIGNATURE.length];
		dataIn.readFully(signature);
		if (!Arrays.equals(signature, SIGNATURE)) {
			throw new IOException("Invalid terrarium raster signature");
		}

		int version = dataIn.readUnsignedByte();
		if (version != 0) {
			throw new IOException("Unsupported terrarium raster version " + version);
		}

		int width = dataIn.readInt();
		int height = dataIn.readInt();
		int format = dataIn.readUnsignedByte();
		if (format != FORMAT_SHORT) {
			throw new IOException("Expected short raster format");
		}

		ShortRaster raster = ShortRaster.create(width, height);
		while (true) {
			int chunkLength;
			try {
				chunkLength = dataIn.readInt();
			} catch (EOFException e) {
				break;
			}

			byte[] chunkBytes = new byte[chunkLength];
			dataIn.readFully(chunkBytes);
			readChunk(new ByteArrayInputStream(chunkBytes), raster);
		}

		return raster;
	}

	private static void readChunk(InputStream input, ShortRaster raster) throws IOException {
		DataInputStream dataIn = new DataInputStream(input);
		int chunkX = dataIn.readInt();
		int chunkY = dataIn.readInt();
		int chunkWidth = dataIn.readInt();
		int chunkHeight = dataIn.readInt();
		RasterFilter filter = RasterFilter.byId(dataIn.readUnsignedByte());

		short[] raw = new short[chunkWidth * chunkHeight];
		try (SingleXZInputStream xzIn = new SingleXZInputStream(input)) {
			DataInputStream xzData = new DataInputStream(xzIn);
			for (int i = 0; i < raw.length; i++) {
				raw[i] = xzData.readShort();
			}
		}

		applyFilter(filter, raw, chunkWidth, chunkHeight);
		copyChunk(raster, raw, chunkX, chunkY, chunkWidth, chunkHeight);
	}

	private static void applyFilter(RasterFilter filter, short[] raw, int width, int height) {
		if (filter == RasterFilter.NONE) {
			return;
		}

		short[] decoded = new short[raw.length];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int index = x + y * width;
				int value = raw[index];
				int left = x > 0 ? decoded[index - 1] : 0;
				int up = y > 0 ? decoded[index - width] : 0;
				int upLeft = (x > 0 && y > 0) ? decoded[index - width - 1] : 0;
				decoded[index] = (short) filter.apply(value, left, up, upLeft);
			}
		}

		System.arraycopy(decoded, 0, raw, 0, raw.length);
	}

	private static void copyChunk(ShortRaster raster, short[] raw, int chunkX, int chunkY, int width, int height) {
		for (int y = 0; y < height; y++) {
			int destY = chunkY + y;
			if (destY < 0 || destY >= raster.height()) {
				continue;
			}
			for (int x = 0; x < width; x++) {
				int destX = chunkX + x;
				if (destX < 0 || destX >= raster.width()) {
					continue;
				}
				raster.set(destX, destY, raw[x + y * width]);
			}
		}
	}

	private enum RasterFilter {
		NONE {
			@Override
			int apply(int value, int left, int up, int upLeft) {
				return value;
			}
		},
		LEFT {
			@Override
			int apply(int value, int left, int up, int upLeft) {
				return value + left;
			}
		},
		UP {
			@Override
			int apply(int value, int left, int up, int upLeft) {
				return value + up;
			}
		},
		AVERAGE {
			@Override
			int apply(int value, int left, int up, int upLeft) {
				return value + (left + up) / 2;
			}
		},
		PAETH {
			@Override
			int apply(int value, int left, int up, int upLeft) {
				int estimate = left + up - upLeft;
				int deltaLeft = Math.abs(left - estimate);
				int deltaUp = Math.abs(up - estimate);
				int deltaUpLeft = Math.abs(upLeft - estimate);
				if (deltaLeft < deltaUp && deltaLeft < deltaUpLeft) {
					return value + left;
				}
				if (deltaUp < deltaUpLeft) {
					return value + up;
				}
				return value + upLeft;
			}
		};

		abstract int apply(int value, int left, int up, int upLeft);

		static RasterFilter byId(int id) {
			return switch (id) {
				case 1 -> LEFT;
				case 2 -> UP;
				case 3 -> AVERAGE;
				case 4 -> PAETH;
				default -> NONE;
			};
		}
	}
}
