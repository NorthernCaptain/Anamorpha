package northern.captain.anamorph;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Main
{

    public static void main(String[] args)
    {
        if(args.length < 2)
        {
            System.out.println("Usage: anamorph inputFileName outputFileName");
            System.exit(0);
        }

        BufferedImage srcImage = null;
        try
        {
             srcImage = ImageIO.read(new File(args[0]));
        } catch (IOException e)
        {
            System.out.println("Could not read input file: " + args[0]);
            e.printStackTrace();
            System.exit(1);
        }

        Anamorph anamorph = new Anamorph(srcImage);

        BufferedImage destImage = anamorph.doMorph(new Anamorph.Params(250, 600, 60));

        try
        {
            String extention = args[1];
            int idx = extention.lastIndexOf(".");
            if(idx > 0)
            {
                extention = extention.substring(idx+1);
            } else
            {
                extention = "png";
            }

            ImageIO.write(destImage, extention, new File(args[1]));
        } catch (IOException e)
        {
            System.out.println("Could not write output file: " + args[1]);
            e.printStackTrace();
            System.exit(2);
        }
    }
}
