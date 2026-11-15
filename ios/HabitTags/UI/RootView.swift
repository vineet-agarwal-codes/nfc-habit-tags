import SwiftUI
import HabitCore

struct RootView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        TabView {
            NavigationStack { DiscoverView() }
                .tabItem { Label("Discover", systemImage: "sparkles") }
            NavigationStack { MyRecipesView() }
                .tabItem { Label("My recipes", systemImage: "tag") }
        }
        .safeAreaInset(edge: .top, spacing: 0) { ActiveStrip() }
        .overlay(alignment: .bottom) { BannerView() }
        .sheet(item: $model.checklist) { ChecklistView(item: $0) }
        .sheet(item: $model.setupTag) { SetupTagView(tagId: $0.id) }
        .fullScreenCover(item: $model.gate) { GateView(request: $0) }
    }
}

/** Modes that are on right now, each with End now (the typing gate). */
struct ActiveStrip: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        let modes = model.engine.activeModes(nowMs: model.nowMs, tzId: model.tz)
        if !modes.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text("On now").font(.caption).foregroundStyle(.secondary)
                ForEach(modes, id: \.key) { mode in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(mode.title).font(.headline)
                            let detail = [mode.endsText, mode.strictText].filter { !$0.isEmpty }.joined(separator: " · ")
                            if !detail.isEmpty { Text(detail).font(.caption) }
                        }
                        Spacer()
                        Button("End now") { model.gate = GateRequest(id: mode.key, title: mode.title) }
                            .buttonStyle(.bordered)
                    }
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.thinMaterial)
        }
    }
}

struct BannerView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        if let text = model.banner {
            Text(text)
                .font(.callout)
                .padding(12)
                .frame(maxWidth: .infinity)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, 16)
                .padding(.bottom, 64)
                .onTapGesture { model.banner = nil }
                .task(id: text) {
                    try? await Task.sleep(nanoseconds: 4_000_000_000)
                    if model.banner == text { model.banner = nil }
                }
        }
    }
}

struct ChecklistView: View {
    let item: ChecklistItem
    @State private var done: Set<String> = []
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(item.items, id: \.self) { entry in
                Button {
                    if done.contains(entry) { done.remove(entry) } else { done.insert(entry) }
                } label: {
                    Label(entry, systemImage: done.contains(entry) ? "checkmark.circle.fill" : "circle")
                }
            }
            .navigationTitle(item.title)
            .toolbar { Button("Done") { dismiss() } }
        }
    }
}

/** A tag this phone doesn't know: link it to a recipe (installing it if needed). */
struct SetupTagView: View {
    let tagId: String
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Pick what this tag should do") {
                    let installed = model.engine.myRecipes(tzId: model.tz).filter { $0.usable }
                    let others = model.engine.discover(nowMs: model.nowMs, tzId: model.tz)
                    ForEach(installed + others, id: \.id) { card in
                        Button(card.title) {
                            model.link(tagId: tagId, recipeId: card.id)
                            model.banner = "Tag linked to \(card.title). Tap it again to start."
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle("Set up this tag")
            .toolbar { Button("Cancel") { dismiss() } }
        }
    }
}
