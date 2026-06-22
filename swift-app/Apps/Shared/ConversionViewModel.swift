import Foundation
import SwiftUI
import HeicJpgKit

private actor ConversionWorker {
    private let converter = HeicConverter()
    private let cleaner = ConvertedSourceCleaner()

    func convert(
        image: ScannedImage,
        outputDirectory: URL?,
        options: ConversionOptions
    ) -> ConversionResult {
        converter.convert(image: image, outputDirectory: outputDirectory, options: options)
    }

    func delete(_ candidates: [CleanupCandidate]) -> [CleanupResult] {
        cleaner.delete(candidates)
    }
}

/// Drives the SwiftUI UI on both macOS and iOS.
@MainActor
final class ConversionViewModel: ObservableObject {
    @Published private(set) var images: [ScannedImage] = []
    @Published private(set) var results: [ConversionResult] = []
    @Published private(set) var cleanupResults: [CleanupResult] = []
    @Published private(set) var isRunning = false
    @Published private(set) var completedCount = 0

    // Bound to the options UI.
    @Published var maxDimensionText: String = ""
    @Published var jpegQuality: Double = 0.9
    @Published var overwrite: Bool = false
    @Published var outputDirectory: URL?

    private let scanner = ImageFileScanner()
    private let converter = HeicConverter()
    private let cleaner = ConvertedSourceCleaner()
    private let worker = ConversionWorker()

    /// URLs we hold security-scoped access claims on, so we can release them later.
    private var inputScopedURLs: [URL] = []
    private var outputScopedURL: URL?

    init() {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if let flagIndex = arguments.firstIndex(of: "--screenshot-source"),
           arguments.indices.contains(flagIndex + 1) {
            images = scanner.scanImages([
                URL(fileURLWithPath: arguments[flagIndex + 1])
            ])
        }
        #endif
    }

    var sources: [URL] { images.map(\.source) }

    var canRun: Bool { !images.isEmpty && !isRunning && outputCollisions.isEmpty }

    var outputCollisions: [URL] {
        let grouped = Dictionary(grouping: images) { image in
            converter.targetURL(for: image, outputDirectory: outputDirectory)
                .standardizedFileURL.path
        }
        return grouped.compactMap { path, matchingImages in
            matchingImages.count > 1 ? URL(fileURLWithPath: path) : nil
        }.sorted { $0.path < $1.path }
    }

    var cleanupCandidates: [CleanupCandidate] {
        cleaner.candidates(from: images, outputDirectory: outputDirectory)
    }

    var exportableTargets: [URL] {
        results.compactMap { result in
            guard let target = result.target else { return nil }
            switch result.outcome {
            case .converted, .skippedExists:
                return FileManager.default.fileExists(atPath: target.path) ? target : nil
            case .failed:
                return nil
            }
        }
    }

    var summary: String {
        guard !results.isEmpty else {
            if !outputCollisions.isEmpty {
                return "Resolve \(outputCollisions.count) output collision(s)."
            }
            return images.isEmpty ? "No files added yet."
                                  : "\(images.count) file(s) ready."
        }
        let converted = results.filter { $0.didConvert }.count
        let skipped = results.filter { if case .skippedExists = $0.outcome { return true } else { return false } }.count
        let failed = results.filter { $0.didFail }.count
        return "Converted \(converted) · Skipped \(skipped) · Failed \(failed)"
    }

    /// Adds files/folders chosen by the user. Each URL may be security-scoped
    /// (from `.fileImporter`); we claim access and hold it for the session.
    func add(urls: [URL]) {
        for url in urls where url.startAccessingSecurityScopedResource() {
            inputScopedURLs.append(url)
        }
        let discovered = scanner.scanImages(urls)
        var existing = Set(images.map { $0.source.standardizedFileURL.path })
        for image in discovered where existing.insert(image.source.standardizedFileURL.path).inserted {
            images.append(image)
        }
        images.sort { $0.source.path < $1.source.path }
        results.removeAll()
        cleanupResults.removeAll()
    }

    func setOutputDirectory(_ url: URL?) {
        outputScopedURL?.stopAccessingSecurityScopedResource()
        outputScopedURL = nil
        if let url, url.startAccessingSecurityScopedResource() {
            outputScopedURL = url
        }
        outputDirectory = url
        results.removeAll()
        cleanupResults.removeAll()
    }

    func clear() {
        images.removeAll()
        results.removeAll()
        cleanupResults.removeAll()
        releaseScopedURLs()
    }

    func run() async {
        guard canRun else { return }
        isRunning = true
        results = []
        cleanupResults = []
        completedCount = 0

        let options = currentOptions()
        let outputDir = outputDirectory

        for image in images {
            let result = await worker.convert(
                image: image,
                outputDirectory: outputDir,
                options: options
            )
            results.append(result)
            completedCount += 1
        }

        isRunning = false
    }

    func deleteConvertedSources(_ candidates: [CleanupCandidate]) async {
        guard !isRunning, !candidates.isEmpty else { return }
        isRunning = true
        cleanupResults = await worker.delete(candidates)

        let deleted = Set(cleanupResults.compactMap { result -> String? in
            if case .deleted = result.outcome {
                return result.source.standardizedFileURL.path
            }
            return nil
        })
        images.removeAll { deleted.contains($0.source.standardizedFileURL.path) }
        results.removeAll { deleted.contains($0.source.standardizedFileURL.path) }
        isRunning = false
    }

    private func currentOptions() -> ConversionOptions {
        let trimmed = maxDimensionText.trimmingCharacters(in: .whitespaces)
        let maxDimension = trimmed.isEmpty ? nil : Int(trimmed)
        return ConversionOptions(maxDimension: maxDimension,
                                 jpegQuality: jpegQuality,
                                 overwrite: overwrite)
    }

    private func releaseScopedURLs() {
        for url in inputScopedURLs {
            url.stopAccessingSecurityScopedResource()
        }
        inputScopedURLs.removeAll()
        outputScopedURL?.stopAccessingSecurityScopedResource()
        outputScopedURL = nil
    }

    deinit {
        for url in inputScopedURLs {
            url.stopAccessingSecurityScopedResource()
        }
        outputScopedURL?.stopAccessingSecurityScopedResource()
    }
}
