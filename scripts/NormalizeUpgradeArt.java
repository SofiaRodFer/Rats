import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/** java scripts/NormalizeUpgradeArt.java generated.png original-frame.png output.png */
class NormalizeUpgradeArt {
	public static void main(String[] args) throws Exception {
		if (args.length != 3) throw new IllegalArgumentException("generated reference output");
		BufferedImage source = ImageIO.read(new File(args[0])), frame = ImageIO.read(new File(args[1]));
		if (frame.getWidth() != 16 || frame.getHeight() != 16) throw new IllegalArgumentException("Expected original 16px frame");
		BufferedImage result = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		var graphics = result.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(source, 0, 0, 16, 16, null); graphics.dispose();
		// Exact original silhouette, frame and corner fittings, not AI approximations.
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
			if (x < 2 || x >= 14 || y < 2 || y >= 14 || ((x < 4 || x >= 12) && (y < 4 || y >= 12)) || (frame.getRGB(x, y) >>> 24) == 0) result.setRGB(x, y, frame.getRGB(x, y));
		}
		ImageIO.write(result, "png", new File(args[2]));
	}
}
