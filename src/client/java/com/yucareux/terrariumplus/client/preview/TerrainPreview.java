package com.yucareux.terrariumplus.client.preview;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yucareux.terrariumplus.Terrarium;
import com.yucareux.terrariumplus.mixin.client.GuiGraphicsAccessor;
import com.yucareux.terrariumplus.world.data.elevation.TerrariumElevationSource;
import com.yucareux.terrariumplus.worldgen.EarthGeneratorSettings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

public final class TerrainPreview implements AutoCloseable {
	private static final double EQUATOR_CIRCUMFERENCE = 40075017.0;
	private static final int GRID_SIZE = 385;
	private static final int GRID_RADIUS_BLOCKS = 256;
	private static final int GRANULARITY = 1;
	private static final float VERTICAL_SCALE = 0.7f;
	private static final float CAMERA_DISTANCE = 2.35f;
	private static final float FOV = 50.0f;
	private static final float MIN_FOV = 15.0f;
	private static final float MAX_FOV = 120.0f;
	private static final float Z_NEAR = 0.05f;
	private static final float Z_FAR = 100.0f;

	private static final int SHALLOW_SEA_COLOR = 0x4BA7DB;
	private static final int MID_SEA_COLOR = 0x1D5E9B;
	private static final int DEEP_SEA_COLOR = 0x071E3D;
	private static final int SHORE_COLOR = 0xC9B37E;
	private static final int LOW_LAND_COLOR = 0x3F9A53;
	private static final int MID_LAND_COLOR = 0x7F8F4D;
	private static final int HIGH_LAND_COLOR = 0x8C7A64;
	private static final int ROCK_COLOR = 0xA0A0A0;
	private static final int PEAK_COLOR = 0xF5F5F5;
	private static final Vector3f LIGHT_DIR = new Vector3f(-0.4f, 0.8f, -0.4f).normalize();
	private static final float AMBIENT_SHADE = 0.45f;
	private static final float SHADE_STEPS = 8.0f;

	private final TerrariumElevationSource elevationSource = new TerrariumElevationSource();
	private final ExecutorService executor;
	private final AtomicInteger requestId = new AtomicInteger();

	private CompletableFuture<PreviewMesh> pending;
	private PreviewMesh mesh;

	public TerrainPreview() {
		this.executor = Executors.newSingleThreadExecutor(new PreviewThreadFactory());
	}

	public void requestRebuild(EarthGeneratorSettings settings) {
		int id = this.requestId.incrementAndGet();
		if (this.pending != null) {
			this.pending.cancel(true);
		}
		this.pending = CompletableFuture.supplyAsync(() -> buildMesh(settings), this.executor)
				.exceptionally(error -> {
					Terrarium.LOGGER.warn("Failed to build terrain preview", error);
					return null;
				})
				.thenApply(mesh -> {
					if (mesh != null && id != this.requestId.get()) {
						return null;
					}
					return mesh;
				});
	}

	public void tick() {
		CompletableFuture<PreviewMesh> future = this.pending;
		if (future != null && future.isDone()) {
			this.pending = null;
			try {
				PreviewMesh preview = future.join();
				if (preview != null) {
					this.mesh = preview;
				}
			} catch (RuntimeException error) {
				Terrarium.LOGGER.warn("Preview render update failed", error);
			}
		}
	}

	public void render(GuiGraphics graphics, int x, int y, int width, int height, float rotationX, float rotationY, float zoom) {
		PreviewMesh preview = this.mesh;
		if (preview == null || width <= 0 || height <= 0) {
			return;
		}

		Matrix4f modelView = buildModelView(rotationX, rotationY);
		Matrix4f projection = buildProjection(width, height, zoom);
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		ScreenRectangle rawBounds = new ScreenRectangle(x, y, width, height);
		ScreenRectangle bounds = rawBounds.transformAxisAligned(pose);
		GuiRenderState renderState = ((GuiGraphicsAccessor) graphics).terrariumplus$getGuiRenderState();
		renderState.submitGuiElement(new TerrainPreviewRenderState(preview, modelView, projection, pose, rawBounds, bounds));
	}

	private static Matrix4f buildProjection(int width, int height, float zoom) {
		float aspect = width / (float) height;
		float effectiveFov = Mth.clamp(FOV / Math.max(zoom, 0.01f), MIN_FOV, MAX_FOV);
		return new Matrix4f().setPerspective((float) Math.toRadians(effectiveFov), aspect, Z_NEAR, Z_FAR);
	}

	private static Matrix4f buildModelView(float rotationX, float rotationY) {
		return new Matrix4f()
				.identity()
				.translate(0.0f, 0.0f, -CAMERA_DISTANCE)
				.rotateX(rotationX)
				.rotateY(rotationY);
	}

	private PreviewMesh buildMesh(EarthGeneratorSettings settings) {
		int size = GRID_SIZE;
		double[] blockHeights = new double[size * size];
		double[] elevations = new double[size * size];

		double metersPerDegree = EQUATOR_CIRCUMFERENCE / 360.0;
		double blocksPerDegree = metersPerDegree / settings.worldScale();
		double centerX = settings.spawnLongitude() * blocksPerDegree;
		double centerZ = -settings.spawnLatitude() * blocksPerDegree;
		double radius = GRID_RADIUS_BLOCKS;
		double step = (radius * 2.0) / (size - 1);

		for (int z = 0; z < size; z++) {
			double blockZ = centerZ - radius + z * step;
			for (int x = 0; x < size; x++) {
				double blockX = centerX - radius + x * step;
				int idx = x + z * size;
				double elevation = this.elevationSource.sampleElevationMeters(
						blockX,
						blockZ,
						settings.worldScale(),
						false
				);
				elevations[idx] = elevation;
				blockHeights[idx] = applyHeightScale(elevation, settings);
			}
		}

		float[] heights = new float[size * size];
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < blockHeights.length; i++) {
			float value = (float) ((blockHeights[i] - settings.heightOffset()) / radius * VERTICAL_SCALE);
			heights[i] = value;
			min = Math.min(min, value);
			max = Math.max(max, value);
		}
		float center = (min + max) * 0.5f;
		for (int i = 0; i < heights.length; i++) {
			heights[i] -= center;
		}

		int[] colors = new int[size * size];
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				int idx = x + z * size;
				colors[idx] = colorForElevation(elevations[idx]);
			}
		}

		float[] xCoords = new float[size];
		for (int i = 0; i < size; i++) {
			xCoords[i] = (float) (-1.0 + (2.0 * i) / (size - 1));
		}

		return new PreviewMesh(size, GRANULARITY, heights, colors, xCoords);
	}

	private static double applyHeightScale(double elevation, EarthGeneratorSettings settings) {
		double scale = elevation >= 0.0 ? settings.terrestrialHeightScale() : settings.oceanicHeightScale();
		double scaled = elevation * scale / settings.worldScale();
		int base = elevation >= 0.0 ? Mth.ceil(scaled) : Mth.floor(scaled);
		return base + settings.heightOffset();
	}

	private static int colorForElevation(double elevation) {
		if (elevation < 0.0) {
			double depth = -elevation;
			if (depth < 60.0) {
				return lerpColor(SHALLOW_SEA_COLOR, MID_SEA_COLOR, depth / 60.0);
			}
			if (depth < 2000.0) {
				return lerpColor(MID_SEA_COLOR, DEEP_SEA_COLOR, (depth - 60.0) / 1940.0);
			}
			return DEEP_SEA_COLOR;
		}

		if (elevation < 120.0) {
			return lerpColor(SHORE_COLOR, LOW_LAND_COLOR, elevation / 120.0);
		}
		if (elevation < 900.0) {
			return lerpColor(LOW_LAND_COLOR, MID_LAND_COLOR, (elevation - 120.0) / 780.0);
		}
		if (elevation < 2200.0) {
			return lerpColor(MID_LAND_COLOR, HIGH_LAND_COLOR, (elevation - 900.0) / 1300.0);
		}
		if (elevation < 3800.0) {
			return lerpColor(HIGH_LAND_COLOR, ROCK_COLOR, (elevation - 2200.0) / 1600.0);
		}
		if (elevation < 5200.0) {
			return lerpColor(ROCK_COLOR, PEAK_COLOR, (elevation - 3800.0) / 1400.0);
		}
		return PEAK_COLOR;
	}

	private static int lerpColor(int a, int b, double t) {
		double clamped = Mth.clamp(t, 0.0, 1.0);
		int ar = (a >> 16) & 0xFF;
		int ag = (a >> 8) & 0xFF;
		int ab = a & 0xFF;
		int br = (b >> 16) & 0xFF;
		int bg = (b >> 8) & 0xFF;
		int bb = b & 0xFF;
		int r = (int) Math.round(ar + (br - ar) * clamped);
		int g = (int) Math.round(ag + (bg - ag) * clamped);
		int bch = (int) Math.round(ab + (bb - ab) * clamped);
		return (r << 16) | (g << 8) | bch;
	}

	@Override
	public void close() {
		if (this.pending != null) {
			this.pending.cancel(true);
		}
		this.executor.shutdownNow();
	}

	private static final class PreviewMesh {
		private final int size;
		private final int granularity;
		private final float[] heights;
		private final int[] colors;
		private final float[] axis;

		private PreviewMesh(int size, int granularity, float[] heights, int[] colors, float[] axis) {
			this.size = size;
			this.granularity = granularity;
			this.heights = heights;
			this.colors = colors;
			this.axis = axis;
		}
	}

	private static final class TerrainPreviewRenderState implements GuiElementRenderState {
		private final PreviewMesh mesh;
		private final Matrix4f modelView;
		private final Matrix4f projection;
		private final @NonNull Matrix3x2fc pose;
		private final ScreenRectangle rawBounds;
		private final ScreenRectangle bounds;
		private final ScreenRectangle scissor;

		private TerrainPreviewRenderState(
				PreviewMesh mesh,
				Matrix4f modelView,
				Matrix4f projection,
				@NonNull Matrix3x2fc pose,
				ScreenRectangle rawBounds,
				ScreenRectangle bounds
		) {
			this.mesh = mesh;
			this.modelView = modelView;
			this.projection = projection;
			this.pose = pose;
			this.rawBounds = rawBounds;
			this.bounds = bounds;
			this.scissor = bounds;
		}

		@Override
		public @NonNull RenderPipeline pipeline() {
			return RenderPipelines.GUI;
		}

		@Override
		public @NonNull TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}

		@Override
		public ScreenRectangle scissorArea() {
			return this.scissor;
		}

		@Override
		public ScreenRectangle bounds() {
			return this.bounds;
		}

		@Override
		public void buildVertices(@NonNull VertexConsumer consumer) {
			int stride = this.mesh.granularity;
			if (this.mesh.size <= stride) {
				return;
			}

			int quadsX = (this.mesh.size - 1) / stride;
			int quadsZ = (this.mesh.size - 1) / stride;
			int quadCount = quadsX * quadsZ;
			int[] quadTopLeft = new int[quadCount];
			float[] quadDepth = new float[quadCount];
			boolean[] quadVisible = new boolean[quadCount];

			Vector3f view = new Vector3f();
			Vector3f normal = new Vector3f();
			float depthScale = 0.25f;
			int quadIndex = 0;

			for (int z = 0; z < this.mesh.size - stride; z += stride) {
				float z0 = this.mesh.axis[z];
				float z1 = this.mesh.axis[z + stride];
				int rowIndex = z * this.mesh.size;
				int nextRowIndex = (z + stride) * this.mesh.size;
				for (int x = 0; x < this.mesh.size - stride; x += stride) {
					int idx = rowIndex + x;
					int idxRight = idx + stride;
					int idxDown = nextRowIndex + x;
					int idxDownRight = idxDown + stride;

					float v0 = this.modelView.transformPosition(this.mesh.axis[x], this.mesh.heights[idx], z0, view).z;
					float v1 = this.modelView.transformPosition(this.mesh.axis[x + stride], this.mesh.heights[idxRight], z0, view).z;
					float v2 = this.modelView.transformPosition(this.mesh.axis[x], this.mesh.heights[idxDown], z1, view).z;
					float v3 = this.modelView.transformPosition(this.mesh.axis[x + stride], this.mesh.heights[idxDownRight], z1, view).z;
					float maxZ = Math.max(Math.max(v0, v1), Math.max(v2, v3));

					if (maxZ > -Z_NEAR) {
						quadTopLeft[quadIndex] = -1;
						quadDepth[quadIndex] = Float.POSITIVE_INFINITY;
						quadVisible[quadIndex] = false;
					} else {
						float depth = (v0 + v1 + v2 + v3) * depthScale;
						quadTopLeft[quadIndex] = idx;
						quadDepth[quadIndex] = depth;
						quadVisible[quadIndex] = true;
					}
					quadIndex++;
				}
			}

			if (quadCount > 1) {
				sortQuads(quadTopLeft, quadDepth, 0, quadCount - 1);
			}

			Vector3f projected = new Vector3f();
			float x0 = this.rawBounds.left();
			float y0 = this.rawBounds.top();
			float width = this.rawBounds.width();
			float height = this.rawBounds.height();

			for (int i = 0; i < quadCount; i++) {
				int idx = quadTopLeft[i];
				if (idx < 0 || !quadVisible[i]) {
					continue;
				}
				int x = idx % this.mesh.size;
				int z = idx / this.mesh.size;
				int idxRight = idx + stride;
				int idxDown = idx + stride * this.mesh.size;
				int idxDownRight = idxDown + stride;

				float worldX0 = this.mesh.axis[x];
				float worldX1 = this.mesh.axis[x + stride];
				float worldZ0 = this.mesh.axis[z];
				float worldZ1 = this.mesh.axis[z + stride];

				float shade = computeQuadShade(
						worldX0, this.mesh.heights[idx], worldZ0,
						worldX1, this.mesh.heights[idxRight], worldZ0,
						worldX0, this.mesh.heights[idxDown], worldZ1,
						normal
				);
				int quadColor = applyShade(this.mesh.colors[idx], shade);
				emitVertex(consumer, worldX0, this.mesh.heights[idxDown], worldZ1, quadColor, x0, y0, width, height, view, projected);
				emitVertex(consumer, worldX1, this.mesh.heights[idxDownRight], worldZ1, quadColor, x0, y0, width, height, view, projected);
				emitVertex(consumer, worldX1, this.mesh.heights[idxRight], worldZ0, quadColor, x0, y0, width, height, view, projected);
				emitVertex(consumer, worldX0, this.mesh.heights[idx], worldZ0, quadColor, x0, y0, width, height, view, projected);
			}
		}

		private void emitVertex(
				VertexConsumer consumer,
				float worldX,
				float worldY,
				float worldZ,
				int rgb,
				float x0,
				float y0,
				float width,
				float height,
				Vector3f view,
				Vector3f projected
		) {
			this.modelView.transformPosition(worldX, worldY, worldZ, view);
			this.projection.transformProject(view, projected);

			float screenX = x0 + (projected.x + 1.0f) * 0.5f * width;
			float screenY = y0 + (1.0f - projected.y) * 0.5f * height;
			int argb = 0xFF000000 | (rgb & 0x00FFFFFF);
			consumer.addVertexWith2DPose(this.pose, screenX, screenY).setColor(argb);
		}

		private float computeQuadShade(
				float x0,
				float y0,
				float z0,
				float x1,
				float y1,
				float z1,
				float x2,
				float y2,
				float z2,
				Vector3f normal
		) {
			float ax = x1 - x0;
			float ay = y1 - y0;
			float az = z1 - z0;
			float bx = x2 - x0;
			float by = y2 - y0;
			float bz = z2 - z0;

			normal.set(
					ay * bz - az * by,
					az * bx - ax * bz,
					ax * by - ay * bx
			);
			if (normal.y < 0.0f) {
				normal.negate();
			}
			normal.normalize();

			float shade = Mth.clamp(normal.dot(LIGHT_DIR), 0.0f, 1.0f);
			shade = AMBIENT_SHADE + shade * (1.0f - AMBIENT_SHADE);
			return Math.round(shade * SHADE_STEPS) / SHADE_STEPS;
		}

		private static int applyShade(int rgb, float shade) {
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			r = Mth.clamp(Math.round(r * shade), 0, 255);
			g = Mth.clamp(Math.round(g * shade), 0, 255);
			b = Mth.clamp(Math.round(b * shade), 0, 255);
			return (r << 16) | (g << 8) | b;
		}


		private static void sortQuads(int[] quadTopLeft, float[] quadDepth, int left, int right) {
			int i = left;
			int j = right;
			float pivot = quadDepth[(left + right) >>> 1];

			while (i <= j) {
				while (quadDepth[i] < pivot) {
					i++;
				}
				while (quadDepth[j] > pivot) {
					j--;
				}
				if (i <= j) {
					swap(quadTopLeft, quadDepth, i, j);
					i++;
					j--;
				}
			}

			if (left < j) {
				sortQuads(quadTopLeft, quadDepth, left, j);
			}
			if (i < right) {
				sortQuads(quadTopLeft, quadDepth, i, right);
			}
		}

		private static void swap(int[] quadTopLeft, float[] quadDepth, int i, int j) {
			int tempIndex = quadTopLeft[i];
			quadTopLeft[i] = quadTopLeft[j];
			quadTopLeft[j] = tempIndex;

			float tempDepth = quadDepth[i];
			quadDepth[i] = quadDepth[j];
			quadDepth[j] = tempDepth;
		}
	}

	private static final class PreviewThreadFactory implements ThreadFactory {
		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "terrarium-preview");
			thread.setDaemon(true);
			return thread;
		}
	}
}
