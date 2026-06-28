import SwiftUI
import UniformTypeIdentifiers
import HeicJpgKit

struct ContentView: View {
    @StateObject private var viewModel = ConversionViewModel()
    @State private var showingFileImporter = false
    @State private var showingOutputImporter = false
    @State private var showingCleanupConfirmation = false
    @State private var pendingCleanupCandidates: [CleanupCandidate] = []
    @State private var statusMessage: String?

    #if os(iOS)
    @State private var showingShareSheet = false
    @State private var showingFileExporter = false
    @State private var isSavingToPhotos = false
    #endif

    private var importContentTypes: [UTType] {
        [UTType.heic, UTType.heif, UTType.folder].compactMap { $0 }
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    fileSection
                    optionsSection
                    outputSection
                    actionRow
                    resultsSection
                    cleanupSection
                    exportSection
                    Color.clear.frame(height: 1).id("conversion-end")
                }
                .padding(20)
            }
            .onChange(of: viewModel.isRunning) { isRunning in
                if !isRunning, !viewModel.results.isEmpty {
                    withAnimation {
                        proxy.scrollTo("conversion-end", anchor: .bottom)
                    }
                }
            }
        }
        .fileImporter(
            isPresented: $showingFileImporter,
            allowedContentTypes: importContentTypes,
            allowsMultipleSelection: true
        ) { result in
            if case .success(let urls) = result { viewModel.add(urls: urls) }
        }
        .fileImporter(
            isPresented: $showingOutputImporter,
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result { viewModel.setOutputDirectory(urls.first) }
        }
        .task {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--screenshot-convert") {
                await viewModel.run()
            }
            #endif
        }
        .alert(isPresented: $showingCleanupConfirmation) {
            Alert(
                title: Text("Delete converted originals?"),
                message: Text(
                    "Only the \(pendingCleanupCandidates.count) HEIC/HEIF file(s) with a non-empty matching JPEG will be permanently deleted."
                ),
                primaryButton: .destructive(Text("Delete")) {
                    let candidates = pendingCleanupCandidates
                    Task { await viewModel.deleteConvertedSources(candidates) }
                },
                secondaryButton: .cancel()
            )
        }
        #if os(iOS)
        .sheet(isPresented: $showingShareSheet) {
            ActivityView(urls: viewModel.exportableTargets)
        }
        .sheet(isPresented: $showingFileExporter) {
            FileExportPicker(urls: viewModel.exportableTargets) { completed in
                showingFileExporter = false
                if completed { statusMessage = "Exported JPEG files to Files." }
            }
        }
        #endif
    }

    // MARK: Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("HEIC → JPG")
                .font(.largeTitle.bold())
            Text("Convert HEIC/HEIF photos to JPEG.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var fileSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Button {
                    showingFileImporter = true
                } label: {
                    Label("Add files or folder", systemImage: "plus")
                }
                if !viewModel.sources.isEmpty {
                    Button(role: .destructive) {
                        viewModel.clear()
                    } label: {
                        Label("Clear", systemImage: "trash")
                    }
                }
            }

            dropZone

            if !viewModel.sources.isEmpty {
                Text("\(viewModel.sources.count) file(s) selected")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(viewModel.images.prefix(12)) { image in
                            VStack(spacing: 4) {
                                ImageThumbnailView(url: image.source, size: 64)
                                Text(image.source.lastPathComponent)
                                    .font(.caption2)
                                    .lineLimit(1)
                                    .frame(width: 70)
                            }
                        }
                    }
                }
            }
        }
    }

    private var dropZone: some View {
        RoundedRectangle(cornerRadius: 12)
            .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [6]))
            .frame(height: 90)
            .overlay {
                Text(viewModel.sources.isEmpty
                     ? "Drop HEIC files here, or use “Add files”"
                     : "\(viewModel.sources.count) file(s) ready")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            .foregroundStyle(.secondary.opacity(0.5))
            #if os(macOS)
            .onDrop(of: [.fileURL], isTargeted: nil) { providers in
                handleDrop(providers)
            }
            #endif
    }

    private var optionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Options").font(.headline)

            HStack {
                Text("Max dimension")
                TextField("e.g. 2048 (optional)", text: $viewModel.maxDimensionText)
                    #if os(iOS)
                    .keyboardType(.numberPad)
                    #endif
                    .textFieldStyle(.roundedBorder)
                    .frame(maxWidth: 200)
            }

            VStack(alignment: .leading) {
                Text("JPEG quality: \(Int(viewModel.jpegQuality * 100))%")
                Slider(value: $viewModel.jpegQuality, in: 0.1...1.0, step: 0.05)
            }

            Toggle("Overwrite existing .jpg", isOn: $viewModel.overwrite)
        }
    }

    private var outputSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Output").font(.headline)
            HStack {
                Button {
                    showingOutputImporter = true
                } label: {
                    Label("Choose output folder", systemImage: "folder")
                }
                if viewModel.outputDirectory != nil {
                    Button("Reset") { viewModel.setOutputDirectory(nil) }
                }
            }
            Text(viewModel.outputDirectory?.path ?? "Saving next to each original file")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .lineLimit(2)
                .truncationMode(.middle)

            if let collision = viewModel.outputCollisions.first {
                Label(
                    "Multiple inputs map to \(collision.lastPathComponent). Choose another output or remove a duplicate name.",
                    systemImage: "exclamationmark.triangle.fill"
                )
                .font(.footnote)
                .foregroundStyle(.orange)
            }
        }
    }

    private var actionRow: some View {
        HStack {
            Button {
                Task { await viewModel.run() }
            } label: {
                if viewModel.isRunning {
                    ProgressView()
                } else {
                    Text("Convert").bold()
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(!viewModel.canRun)

            Spacer()

            Text(viewModel.summary)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var resultsSection: some View {
        if !viewModel.results.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text("Results").font(.headline)
                ForEach(viewModel.results) { result in
                    ResultRow(result: result)
                }
            }
        }
    }

    @ViewBuilder
    private var cleanupSection: some View {
        let candidates = viewModel.cleanupCandidates
        if !candidates.isEmpty || !viewModel.cleanupResults.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("Original cleanup").font(.headline)
                Text("Only HEIC/HEIF files with a non-empty JPEG at the expected output path are eligible.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                if !candidates.isEmpty {
                    Button(role: .destructive) {
                        pendingCleanupCandidates = candidates
                        showingCleanupConfirmation = true
                    } label: {
                        Label(
                            "Delete \(candidates.count) converted original(s)",
                            systemImage: "trash"
                        )
                    }
                    .disabled(viewModel.isRunning)
                }

                if !viewModel.cleanupResults.isEmpty {
                    Text(cleanupSummary)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var exportSection: some View {
        #if os(iOS)
        if !viewModel.exportableTargets.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("Save or share").font(.headline)
                HStack {
                    Button {
                        saveToPhotos()
                    } label: {
                        Label("Photos", systemImage: "photo.on.rectangle")
                    }
                    .disabled(isSavingToPhotos)

                    Button {
                        showingFileExporter = true
                    } label: {
                        Label("Files", systemImage: "folder")
                    }

                    Button {
                        showingShareSheet = true
                    } label: {
                        Label("Share", systemImage: "square.and.arrow.up")
                    }
                }
                .buttonStyle(.bordered)

                if let statusMessage {
                    Text(statusMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        #endif
    }

    private var cleanupSummary: String {
        let deleted = viewModel.cleanupResults.filter {
            if case .deleted = $0.outcome { return true }
            return false
        }.count
        let failed = viewModel.cleanupResults.count - deleted
        return "Deleted \(deleted) original(s) · Kept/failed \(failed)"
    }

    #if os(iOS)
    private func saveToPhotos() {
        let urls = viewModel.exportableTargets
        guard !urls.isEmpty else { return }
        isSavingToPhotos = true
        statusMessage = nil
        Task {
            do {
                let count = try await PhotoLibrarySaver.save(urls)
                statusMessage = "Saved \(count) JPEG file(s) to Photos."
            } catch {
                statusMessage = error.localizedDescription
            }
            isSavingToPhotos = false
        }
    }
    #endif

    // MARK: macOS drag & drop

    #if os(macOS)
    private func handleDrop(_ providers: [NSItemProvider]) -> Bool {
        let group = DispatchGroup()
        let urls = LockedURLAccumulator()
        for provider in providers {
            group.enter()
            _ = provider.loadObject(ofClass: URL.self) { url, _ in
                if let url { urls.append(url) }
                group.leave()
            }
        }
        group.notify(queue: .main) {
            let droppedURLs = urls.values
            if !droppedURLs.isEmpty { viewModel.add(urls: droppedURLs) }
        }
        return true
    }
    #endif
}

#if os(macOS)
private final class LockedURLAccumulator: @unchecked Sendable {
    private let lock = NSLock()
    private var storage: [URL] = []

    func append(_ url: URL) {
        lock.lock()
        storage.append(url)
        lock.unlock()
    }

    var values: [URL] {
        lock.lock()
        defer { lock.unlock() }
        return storage
    }
}
#endif

private struct ResultRow: View {
    let result: ConversionResult

    var body: some View {
        HStack(spacing: 10) {
            HStack(spacing: 5) {
                ImageThumbnailView(url: result.source, size: 48)
                Image(systemName: "arrow.right")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                ImageThumbnailView(url: previewTarget, size: 48)
            }
            VStack(alignment: .leading, spacing: 2) {
                Label(result.source.lastPathComponent, systemImage: icon)
                    .font(.callout)
                    .foregroundStyle(color)
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            Spacer()
        }
        .padding(.vertical, 2)
    }

    private var previewTarget: URL? {
        switch result.outcome {
        case .converted, .skippedExists: return result.target
        case .failed: return nil
        }
    }

    private var icon: String {
        switch result.outcome {
        case .converted: return "checkmark.circle.fill"
        case .skippedExists: return "minus.circle.fill"
        case .failed: return "xmark.octagon.fill"
        }
    }

    private var color: Color {
        switch result.outcome {
        case .converted: return .green
        case .skippedExists: return .orange
        case .failed: return .red
        }
    }

    private var detail: String {
        switch result.outcome {
        case .converted: return result.target?.lastPathComponent ?? "Converted"
        case .skippedExists: return "Skipped — target already exists"
        case .failed(let message): return message
        }
    }
}
