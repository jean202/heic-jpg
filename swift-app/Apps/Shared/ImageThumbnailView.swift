import SwiftUI
import ImageIO

private actor ImageThumbnailLoader {
    static let shared = ImageThumbnailLoader()

    private var cache: [URL: CGImage] = [:]

    func thumbnail(for url: URL, maxPixelSize: Int = 160) -> CGImage? {
        if let cached = cache[url] { return cached }
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(
            source,
            0,
            options as CFDictionary
        ) else { return nil }
        cache[url] = image
        return image
    }
}

struct ImageThumbnailView: View {
    let url: URL?
    var size: CGFloat = 64

    @State private var image: CGImage?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.12))

            if let image {
                Image(decorative: image, scale: 1)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "photo")
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .task(id: url) {
            guard let url else {
                image = nil
                return
            }
            image = await ImageThumbnailLoader.shared.thumbnail(for: url)
        }
    }
}
