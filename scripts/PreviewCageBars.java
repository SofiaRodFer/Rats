import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.regex.Pattern;

/** Front-projection asset QA, not a Minecraft screenshot. Reads actual model/texture. */
class PreviewCageBars {
	public static void main(String[] args) throws Exception {
		var texture = ImageIO.read(new File("src/main/resources/assets/rats/textures/block/iron_bars.png"));
		var model = Files.readString(new File("src/main/resources/assets/rats/models/block/rat_cage_bars.json").toPath());
		var output = new BufferedImage(704, 374, BufferedImage.TYPE_INT_ARGB); var g = output.createGraphics();
		g.setColor(new Color(34,37,40)); g.fillRect(0,0,704,374); g.setFont(new Font("SansSerif",Font.PLAIN,16));
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		for (int pane = 0; pane < 2; pane++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) { g.setColor((x/4+y/4)%2==0 ? new Color(143,147,150) : new Color(180,184,187)); g.fillRect(24+pane*352+x*18,42+y*18,18,18); }
		g.drawImage(texture,24,42,288,288,null); boolean[][] covered = new boolean[16][16];
		var boxes = Pattern.compile("\"from\"\\s*:\\s*\\[([^]]+)\\].*?\"to\"\\s*:\\s*\\[([^]]+)\\]",Pattern.DOTALL).matcher(model);
		while (boxes.find()) {
			String[] from=boxes.group(1).split(","), to=boxes.group(2).split(","); int x0=(int)Double.parseDouble(from[0]), x1=(int)Double.parseDouble(to[0]), y0=(int)Double.parseDouble(from[1]), y1=(int)Double.parseDouble(to[1]);
			for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){covered[y][x]=true; g.setColor(new Color(texture.getRGB(3,y1-y0>1?15-y:0),true));g.fillRect(376+x*18,42+(15-y)*18,18,18);}
		}
		int oldOpaque=0,newOpaque=0; for(int y=0;y<16;y++)for(int x=0;x<16;x++){if((texture.getRGB(x,y)>>>24)>0)oldOpaque++;if(covered[y][x])newOpaque++;}
		g.setColor(Color.WHITE); g.drawString("Original texture",24,26);g.drawString("New native bar geometry",376,26);
		g.drawString("Clear area: "+Math.round((256-oldOpaque)*100f/256)+"%",24,352);g.drawString("Clear area: "+Math.round((256-newOpaque)*100f/256)+"%",376,352);g.dispose();
		ImageIO.write(output,"png",new File(args[0]));System.out.println("Front projection: original opaque="+oldOpaque+", new opaque="+newOpaque+" of 256 cells");
	}
}
