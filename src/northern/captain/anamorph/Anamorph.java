package northern.captain.anamorph;

import java.awt.image.BufferedImage;

/**
 * Created with IntelliJ IDEA.
 * User: NorthernCaptain
 * Date: 04.04.14
 * Time: 14:19
 * To change this template use File | Settings | File Templates.
 */
public class Anamorph
{
    BufferedImage src;
    public Anamorph(BufferedImage srcImage)
    {
        src = srcImage;
    }

    public static class Params
    {
        public double minRadius;
        public double maxRadius;
        public double radLength;
        public double angle;

        public int width;
        public int height;

        public double centerX, centerY;
        public double part;

        public Params(int minR, int maxR, double degrees)
        {
            width = maxR;
            
            maxR = width*6/13;
            
            minRadius = minR;
            maxRadius = maxR;
            radLength = maxRadius - minRadius;

            angle = Math.toRadians(degrees/2.0);

            centerX = width / 2.0;

            height = width;
            centerY = height / 2.0;

            part = (180 + degrees) / 360.0;
        }
    }

    private int[] pixels;

    private int[] destpixels;

    public BufferedImage doMorph(Params params)
    {
        int width = src.getWidth();
        int height = src.getHeight();

        int dWidth = params.width;
        int dHeight = params.height;

        BufferedImage dest = new BufferedImage(dWidth, dHeight, BufferedImage.TYPE_INT_ARGB);

        pixels = src.getRGB(0, 0, width, height, null, 0, width);

        destpixels = new int[dWidth * dHeight];

        for(int x = 0;x<dWidth;x++)
        {
            double dx = x - params.centerX;
            double deltaX2 = (dx);
            deltaX2 *= deltaX2;

            for(int y = 0;y<dHeight;y++)
            {
                double dy = y - params.centerY;
                double radius = Math.sqrt(deltaX2 + dy*dy);

                double dSrcY = ((radius - params.minRadius)*height/params.radLength);
                int srcY = (int)dSrcY;

                if(srcY < 0 || srcY >= height)
                    continue;

                double dSrcX = radius * (params.angle + Math.asin((dx + radius)/ radius - 1));
                double circleLen = 2.0 * Math.PI * radius * params.part;

                dSrcX = ((dy < 0 ? dSrcX : circleLen - dSrcX) / circleLen * width);
                int srcX = (int)dSrcX;

                if(srcX < 0 || srcX >= width)
                    continue;

//                destpixels[x + y*dWidth] = getDestinationPixel(x, y, dWidth, srcX, (height - srcY - 1), width, dSrcX, circleLen);
                destpixels[(dWidth-y) + x*dWidth] = getDestinationPixel(x, y, dWidth, srcX, (height - srcY - 1), width, dSrcX, circleLen);
            }
        }

        dest.setRGB(0, 0, dWidth, dHeight, destpixels, 0, dWidth);
        return dest;
    }

    private int getDestinationPixel(int destX, int destY, int dWidth, int srcX, int srcY, int width, double dSrcX, double circleLen)
    {
        int backX1 = (int)(srcX * circleLen / width);
        int backX2 = (int)((srcX+1) * circleLen / width);
        int backLen = backX2 - backX1;

        if(backLen < 0 || dSrcX < 0)
            return pixels[srcX + srcY * width];

        double dBackX = (int)(dSrcX * circleLen / width);
        double frac = (dBackX - backX1) / backLen;

        int ifrac = (int)(frac * 1000);

        int idx = srcX + srcY * width;

        return mergePixels(pixels[idx], idx < pixels.length - 1 ? pixels[idx + 1] : 0, ifrac);
    }

    private int mergePixels(int from, int to, int perc)
    {
        int negate =  perc;
        perc = 1000 - perc;
        int result = 0;

        {
            int f1 = from & 0xff;
            int t1 = to & 0xff;

            result |= ((f1*perc + t1*negate)/1000) & 0xff;
        }
        {
            int f1 = (from >> 8) & 0xff;
            int t1 = (to >> 8) & 0xff;

            result |= (((f1*perc + t1*negate)/1000) & 0xff) << 8;
        }
        {
            int f1 = (from >> 16) & 0xff;
            int t1 = (to >> 16) & 0xff;

            result |= (((f1*perc + t1*negate)/1000) & 0xff) << 16;
        }
        {
            int f1 = (from >> 24) & 0xff;
            int t1 = (to >> 24) & 0xff;

            result |= (((f1*perc + t1*negate)/1000) & 0xff) << 24;
        }

        return result;
    }
}
