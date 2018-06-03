package com.lisi.titan;

public class CaptureFormat {
    // Class to represent a framerate range. The framerate varies because of lightning conditions.
    // The values are multiplied by 1000, so 1000 represents one frame per second.
    public static class FramerateRange {
        public int min;
        public int max;

        public FramerateRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString() {
            return "[" + (min / 1000.0f) + ":" + (max / 1000.0f) + "]";
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof FramerateRange)) {
                return false;
            }
            final FramerateRange otherFramerate = (FramerateRange) other;
            return min == otherFramerate.min && max == otherFramerate.max;
        }

        @Override
        public int hashCode() {
            // Use prime close to 2^16 to avoid collisions for normal values less than 2^16.
            return 1 + 65537 * min + max;
        }
    }

    public final int width;
    public final int height;
    public final FramerateRange framerate;

    // TODO(hbos): If VideoCapturer.startCapture is updated to support other image formats then this
    // needs to be updated and VideoCapturer.getSupportedFormats need to return CaptureFormats of
    // all imageFormats.

    public CaptureFormat(int width, int height, FramerateRange framerate) {
        this.width = width;
        this.height = height;
        this.framerate = framerate;
    }

    @Override
    public String toString() {
        return width + "x" + height + "@" + framerate;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CaptureFormat)) {
            return false;
        }
        final CaptureFormat otherFormat = (CaptureFormat) other;
        return width == otherFormat.width && height == otherFormat.height
                && framerate.equals(otherFormat.framerate);
    }

    @Override
    public int hashCode() {
        return 1 + (width * 65497 + height) * 251 + framerate.hashCode();
    }
}

