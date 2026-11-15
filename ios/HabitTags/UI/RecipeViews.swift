import SwiftUI
import HabitCore

struct DiscoverView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        let cards = model.engine.discover(nowMs: model.nowMs, tzId: model.tz)
        List {
            if cards.isEmpty {
                Text("You've installed every recipe — more coming soon.").foregroundStyle(.secondary)
            }
            ForEach(cards, id: \.id) { card in
                NavigationLink(value: card.id) { RecipeRow(card: card) }
            }
        }
        .navigationTitle("Discover")
        .navigationDestination(for: String.self) { RecipeView(recipeId: $0) }
        .refreshable { await model.refreshCatalog() }
    }
}

struct MyRecipesView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        let cards = model.engine.myRecipes(tzId: model.tz)
        List {
            if cards.isEmpty {
                Text("Nothing installed yet. Pick a recipe in Discover.").foregroundStyle(.secondary)
            }
            ForEach(cards, id: \.id) { card in
                NavigationLink(value: card.id) {
                    VStack(alignment: .leading, spacing: 4) {
                        RecipeRow(card: card, showStats: false)
                        Text(tagCountText(card.tagCount)).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle("My recipes")
        .navigationDestination(for: String.self) { RecipeView(recipeId: $0) }
        .toolbar {
            if model.nfcAvailable {
                Button { model.scanTag() } label: { Label("Scan a tag", systemImage: "wave.3.right") }
            }
        }
    }
}

func tagCountText(_ n: Int32) -> String {
    switch n {
    case 0: return "No tag yet"
    case 1: return "1 tag"
    default: return "\(n) tags"
    }
}

struct RecipeRow: View {
    let card: RecipeCard
    var showStats = true

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(card.title).font(.headline)
                if card.isOn { Text("On now").font(.caption.bold()).foregroundStyle(.green) }
            }
            if showStats { Text(card.summary).font(.subheadline).foregroundStyle(.secondary).lineLimit(2) }
            if !card.hours.isEmpty { Text(card.hours).font(.caption) }
            if showStats, !card.stats.isEmpty { Text(card.stats.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary) }
        }
        .padding(.vertical, 4)
    }
}

/** One screen for a recipe: details and Install when not installed; Start now, settings, tags and Uninstall once it is. */
struct RecipeView: View {
    let recipeId: String
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var outside: TapOutcome?

    var body: some View {
        if let card = model.engine.recipeCard(id: recipeId, tzId: model.tz) {
            content(card)
                .navigationTitle(card.title)
                .navigationBarTitleDisplayMode(.inline)
                .alert("Outside its usual hours", isPresented: Binding(get: { outside != nil }, set: { if !$0 { outside = nil } })) {
                    Button("Start anyway") { model.startNow(recipeId: recipeId, force: true) }
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("\(card.title) normally turns on \(outside?.windowsText ?? ""). It's \(clockNow()) now. Start it anyway?")
                }
        } else {
            Text("This recipe isn't available.")
        }
    }

    private func content(_ card: RecipeCard) -> some View {
        let installed = model.engine.isInstalled(id: recipeId)
        let tags = model.engine.tags(recipeId: recipeId)
        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(card.title).font(.title2.bold())
                if card.retired { Text("No longer offered").font(.caption.bold()) }
                Text(card.summary)
                Text("Where to put it: \(card.placementHint)").font(.callout).foregroundStyle(.secondary)
                if !card.stats.isEmpty { Text(card.stats.joined(separator: " · ")).font(.caption) }
                if !card.hours.isEmpty { Text(card.hours).font(.callout) }
                if card.blocksApps { Note(text: "Blocking apps is coming soon on iPhone.") }
                if card.usesDnd { Note(text: "Turn on a Focus yourself while this runs; iPhone apps can't switch it for you.") }

                if !card.usable {
                    Note(text: "Update the app to use this.")
                } else if !installed {
                    Button("Install") { model.install(recipeId: recipeId) }
                        .buttonStyle(.borderedProminent)
                        .frame(maxWidth: .infinity)
                } else {
                    if card.isOn {
                        Text("On now").font(.headline).foregroundStyle(.green)
                    } else {
                        Button(card.oneShot ? "Run now" : "Start now") {
                            let out = model.startNow(recipeId: recipeId, force: false)
                            if out.kind == "outsideHours" { outside = out }
                        }
                        .buttonStyle(.borderedProminent)
                    }

                    let params = model.engine.params(recipeId: recipeId)
                    ForEach(params, id: \.key) { ParamRow(recipeId: recipeId, param: $0) }
                    if !params.isEmpty {
                        Text("Changes apply the next time it starts. Tags don't need rewriting.").font(.caption).foregroundStyle(.secondary)
                    }

                    Text("Tags").font(.headline)
                    Text(tagCountText(Int32(tags.count))).font(.callout)
                    ForEach(tags, id: \.tagId) { tag in
                        HStack {
                            Text(tag.label + (tag.isOn ? " · on" : ""))
                            Spacer()
                            if !tag.isOn {
                                Button("Remove", role: .destructive) { model.removeTag(tagId: tag.tagId) }.font(.caption)
                            }
                        }
                    }
                    if model.nfcAvailable {
                        Button(tags.isEmpty ? "Write to a tag" : "Write another tag") { model.writeTag(recipeId: recipeId) }
                            .buttonStyle(.bordered)
                        Text("NTAG213, 215 or 216 tags work best.").font(.caption).foregroundStyle(.secondary)
                    } else {
                        Note(text: "Tag features need the full version of the app. Use Start now meanwhile.")
                    }

                    if card.isOn { Text("Turn this mode off first").font(.caption).foregroundStyle(.red) }
                    Button("Uninstall", role: .destructive) {
                        if model.uninstall(recipeId: recipeId) { dismiss() }
                    }
                    .disabled(card.isOn)
                }
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func clockNow() -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: Date())
    }
}

struct Note: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.callout)
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
    }
}

/** A time param opens a time picker; other params are sliders. */
struct ParamRow: View {
    let recipeId: String
    let param: ParamView
    @EnvironmentObject var model: AppModel

    var body: some View {
        if param.type == "time" {
            DatePicker(param.label, selection: Binding(
                get: { dateFor(minutes: Int(param.value)) },
                set: { date in
                    let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
                    model.setParam(recipeId: recipeId, key: param.key, value: Int32((parts.hour ?? 0) * 60 + (parts.minute ?? 0)))
                }
            ), displayedComponents: .hourAndMinute)
        } else {
            VStack(alignment: .leading) {
                Text("\(param.label): \(param.valueText)")
                Slider(
                    value: Binding(
                        get: { Double(param.value) },
                        set: { model.setParam(recipeId: recipeId, key: param.key, value: Int32($0.rounded())) }
                    ),
                    in: Double(param.min)...Double(max(param.max, param.min + 1)),
                    step: Double(param.step)
                )
            }
        }
    }

    private func dateFor(minutes: Int) -> Date {
        Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: Date()) ?? Date()
    }
}
