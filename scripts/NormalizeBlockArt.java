import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/** Nearest-neighbor size normalization only; generated artwork is retained. */
class NormalizeBlockArt {
	public static void main(String[] args) throws Exception {
		var source = ImageIO.read(new File(args[0]));
		int size = args.length > 2 ? Integer.parseInt(args[2]) : 16;
		var target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		var graphics = target.createGraphics(); graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(source, 0, 0, size, size, null); graphics.dispose(); ImageIO.write(target, "png", new File(args[1]));
	}
}
