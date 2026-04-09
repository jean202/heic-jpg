package io.github.jean202.heicjpg;

import java.io.IOException;

interface ImageConverter {
    void convert(ConversionTask task, Integer maxDimension) throws IOException, InterruptedException;
}
