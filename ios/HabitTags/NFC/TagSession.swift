import Foundation
import CoreNFC

enum RawTag {
    case url(String)
    case text(String)
}

enum TagError: Error {
    case cancelled
    case needsFullVersion
    case failed(String)

    /** Nil when the user cancelled; nothing to show then. */
    var message: String? {
        switch self {
        case .cancelled: return nil
        case .needsFullVersion: return "Tag features need the full version of the app (coming with TestFlight). Use Start now meanwhile."
        case .failed(let text): return text
        }
    }
}

/**
 * Reads and writes our tags with CoreNFC. iPhone tags hold one https link record; tags written by the
 * Android app hold an external record with the bare tag id, which is read too.
 */
final class TagSession: NSObject, NFCNDEFReaderSessionDelegate {
    private enum Job {
        case read((Result<RawTag, TagError>) -> Void)
        case write(String, (Result<Void, TagError>) -> Void)
    }

    private var session: NFCNDEFReaderSession?
    private var job: Job?

    static var available: Bool { NFCNDEFReaderSession.readingAvailable }

    func read(_ done: @escaping (Result<RawTag, TagError>) -> Void) {
        begin(.read(done), prompt: "Hold your iPhone near the tag.")
    }

    func write(url: String, _ done: @escaping (Result<Void, TagError>) -> Void) {
        begin(.write(url, done), prompt: "Hold your iPhone near a blank tag to write it.")
    }

    private func begin(_ newJob: Job, prompt: String) {
        guard TagSession.available else {
            finish(.needsFullVersion)
            return
        }
        job = newJob
        session = NFCNDEFReaderSession(delegate: self, queue: nil, invalidateAfterFirstRead: false)
        session?.alertMessage = prompt
        session?.begin()
    }

    private func finish(_ error: TagError) {
        let current = job
        job = nil
        DispatchQueue.main.async {
            switch current {
            case .read(let done): done(.failure(error))
            case .write(_, let done): done(.failure(error))
            case nil: break
            }
        }
    }

    private func succeed(_ session: NFCNDEFReaderSession, read value: RawTag? = nil, message: String) {
        let current = job
        job = nil
        session.alertMessage = message
        session.invalidate()
        DispatchQueue.main.async {
            switch current {
            case .read(let done): if let value { done(.success(value)) } else { done(.failure(.failed("This isn't a Habit Tags tag."))) }
            case .write(_, let done): done(.success(()))
            case nil: break
            }
        }
    }

    private func fail(_ session: NFCNDEFReaderSession, _ text: String) {
        session.invalidate(errorMessage: text)
        finish(.failed(text))
    }

    // MARK: NFCNDEFReaderSessionDelegate

    func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        guard job != nil else { return }
        let code = (error as? NFCReaderError)?.code
        switch code {
        case .readerSessionInvalidationErrorUserCanceled, .readerSessionInvalidationErrorSessionTimeout:
            finish(.cancelled)
        case .readerErrorSecurityViolation, .readerErrorUnsupportedFeature:
            finish(.needsFullVersion)
        default:
            finish(.failed(error.localizedDescription))
        }
    }

    func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {}

    func readerSession(_ session: NFCNDEFReaderSession, didDetect tags: [NFCNDEFTag]) {
        guard let tag = tags.first else { return }
        session.connect(to: tag) { error in
            if error != nil { return self.fail(session, "Hold the phone still near the tag.") }
            tag.queryNDEFStatus { status, capacity, error in
                if error != nil { return self.fail(session, "Hold the phone still near the tag.") }
                switch self.job {
                case .read:
                    tag.readNDEF { message, _ in
                        self.succeed(session, read: message.flatMap(Self.parse), message: "Got it.")
                    }
                case .write(let url, _):
                    guard status != .readOnly else { return self.fail(session, "This tag is locked.") }
                    guard status != .notSupported else { return self.fail(session, "This tag can't hold a link.") }
                    guard let link = URL(string: url), let record = NFCNDEFPayload.wellKnownTypeURIPayload(url: link) else {
                        return self.fail(session, "Couldn't create the tag link.")
                    }
                    let message = NFCNDEFMessage(records: [record])
                    guard message.length <= capacity else { return self.fail(session, "Tag too small. NTAG213/215/216 tags work best.") }
                    tag.writeNDEF(message) { error in
                        if error != nil { return self.fail(session, "Hold the phone still near the tag.") }
                        self.succeed(session, message: "Tag written.")
                    }
                case nil:
                    session.invalidate()
                }
            }
        }
    }

    private static func parse(_ message: NFCNDEFMessage) -> RawTag? {
        for record in message.records {
            if let url = record.wellKnownTypeURIPayload() { return .url(url.absoluteString) }
            if record.typeNameFormat == .nfcExternal,
               String(decoding: record.type, as: UTF8.self) == "com.habittags.app:tag" {
                return .text(String(decoding: record.payload, as: UTF8.self))
            }
        }
        return nil
    }
}
