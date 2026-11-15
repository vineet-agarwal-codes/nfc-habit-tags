import SwiftUI
import UIKit
import HabitCore

/**
 * Typing gate: copy the passage letter by letter to end a mode without its tag. Same layout as
 * Android: word counter, the next letter highlighted (red on a typo), and the input box on top of the keyboard.
 */
struct GateView: View {
    let request: GateRequest
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var passage = ""
    @State private var typed = ""
    @State private var rejected = false

    private var gate: FrictionGate { FrictionGate.shared }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("End \(request.title) early?").font(.title3.bold())
                Text("Copy the text below into the box, letter by letter. Or tap your tag to end it instantly.")
                    .font(.callout).foregroundStyle(.secondary)
                if !passage.isEmpty {
                    let matched = Int(gate.matchedLength(typed: typed, target: passage))
                    let typo = gate.hasTypo(typed: typed, target: passage)
                    HStack {
                        Text("\(gate.wordsTyped(typed: typed, target: passage)) of \(gate.wordCount(target: passage)) words")
                            .font(.callout.bold())
                        ProgressView(value: Double(matched), total: Double(max(passage.count, 1)))
                    }
                    ScrollView {
                        remaining(from: matched, typo: typo)
                            .accessibilityIdentifier("gate-passage")
                            .accessibilityLabel(passage)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                    }
                    .background(Color.secondary.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
                    if rejected {
                        Text("Type it letter by letter — dictation, suggestions and paste don't count.").font(.caption).foregroundStyle(.red)
                    } else if typo {
                        Text("Typo — delete back and fix the highlighted letter.").font(.caption).foregroundStyle(.red)
                    }
                    GateTextView(text: $typed, rejected: $rejected)
                        .frame(height: 80)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(typo ? Color.red : Color.accentColor, lineWidth: 2))
                        .accessibilityIdentifier("gate-input")
                }
            }
            .padding()
            .toolbar { Button("Cancel") { dismiss() } }
            .onAppear { if passage.isEmpty { passage = model.engine.gatePassage(words: model.gateWords) } }
            .onChange(of: typed) { value in
                if gate.isComplete(typed: value, target: passage) {
                    model.endViaGate(modeKey: request.id)
                    dismiss()
                }
            }
        }
    }

    /** Shows only what's left to type, starting with the next letter, so the position never scrolls away. */
    private func remaining(from matched: Int, typo: Bool) -> Text {
        let chars = Array(passage)
        guard matched < chars.count else { return Text("") }
        var next = AttributedString(String(chars[matched]))
        next.swiftUI.backgroundColor = typo ? Color.red.opacity(0.35) : Color.yellow.opacity(0.6)
        next.swiftUI.font = .body.bold()
        let rest = AttributedString(String(chars[(matched + 1)...]))
        return Text(next + rest)
    }
}

/** UITextView that refuses paste and any edit inserting more than a few characters at once. */
struct GateTextView: UIViewRepresentable {
    @Binding var text: String
    @Binding var rejected: Bool

    func makeUIView(context: Context) -> NoPasteTextView {
        let view = NoPasteTextView()
        view.delegate = context.coordinator
        view.font = .preferredFont(forTextStyle: .body)
        view.autocorrectionType = .no
        view.spellCheckingType = .no
        view.autocapitalizationType = .none
        view.smartQuotesType = .no
        view.smartDashesType = .no
        view.smartInsertDeleteType = .no
        view.keyboardType = .asciiCapable
        view.backgroundColor = .clear
        view.accessibilityIdentifier = "gate-input"
        view.textContainerInset = UIEdgeInsets(top: 10, left: 8, bottom: 10, right: 8)
        DispatchQueue.main.async { view.becomeFirstResponder() }
        return view
    }

    func updateUIView(_ view: NoPasteTextView, context: Context) {
        if view.text != text { view.text = text }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: GateTextView
        init(_ parent: GateTextView) { self.parent = parent }

        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
            let before = textView.text ?? ""
            let after = (before as NSString).replacingCharacters(in: range, with: text)
            let ok = FrictionGate.shared.acceptEdit(before: before, after: after)
            parent.rejected = !ok
            return ok
        }

        func textViewDidChange(_ textView: UITextView) {
            parent.text = textView.text ?? ""
        }
    }
}

final class NoPasteTextView: UITextView {
    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        if action == #selector(paste(_:)) || action == #selector(UIResponderStandardEditActions.pasteAndMatchStyle(_:)) {
            return false
        }
        return super.canPerformAction(action, withSender: sender)
    }

    override func paste(_ sender: Any?) {}
}
