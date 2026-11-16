package slimeknights.mantle.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Synchronous replacement for {@link net.minecraft.client.Screenshot#takeScreenshot(RenderTarget, java.util.function.Consumer)}.
 * 1.21.5+: vanilla reads the framebuffer back asynchronously, handing the image to a callback once a GPU fence passes on a later
 * frame, and forces every pixel opaque. Callers that must have the image right away (book export, uitest captures) use this instead.
 */
public class RenderTargetCapture {
  private RenderTargetCapture() {}

  /**
   * Reads the color texture of the given target into a new image, flipped so the top row comes first.
   * Must be called on the render thread, outside a render pass.
   * @param target     Render target to read
   * @param keepAlpha  If true, keeps the alpha channel as rendered; if false, forces every pixel opaque like vanilla screenshots
   * @return  Image with the target contents, caller is responsible for closing it
   */
  public static NativeImage capture(RenderTarget target, boolean keepAlpha) {
    RenderSystem.assertOnRenderThread();
    GpuTexture texture = target.getColorTexture();
    if (texture == null) {
      throw new IllegalStateException("Tried to capture an incomplete framebuffer");
    }
    int width = target.width;
    int height = target.height;
    int pixelSize = texture.getFormat().pixelSize();
    if (pixelSize != 4) {
      // the copy below reads each pixel as a 4 byte ABGR int
      throw new IllegalStateException("Can only capture 4 byte per pixel framebuffers, got format " + texture.getFormat());
    }
    int alphaMask = keepAlpha ? 0 : 0xFF000000;
    GpuDevice device = RenderSystem.getDevice();
    CommandEncoder encoder = device.createCommandEncoder();
    try (GpuBuffer buffer = device.createBuffer(() -> "Mantle capture buffer", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, width * height * pixelSize)) {
      // the fenced task is not needed: mapping the buffer for reading below waits for the copy to finish
      encoder.copyTextureToBuffer(texture, buffer, 0, () -> {}, 0);
      try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, true, false)) {
        NativeImage image = new NativeImage(width, height, false);
        try {
          for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
              int abgr = view.data().getInt((x + y * width) * pixelSize);
              // GL rows start at the bottom
              image.setPixelABGR(x, height - y - 1, abgr | alphaMask);
            }
          }
          return image;
        } catch (RuntimeException | Error e) {
          // caller only owns the image once it is returned
          image.close();
          throw e;
        }
      }
    }
  }
}
