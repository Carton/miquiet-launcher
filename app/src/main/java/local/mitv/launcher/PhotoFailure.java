package local.mitv.launcher;

/** Typed storage failures; UI layers choose localized resources without parsing messages. */
final class PhotoFailure extends java.io.IOException {
    enum Reason { LIMIT, DIRECTORY, TOO_LARGE, INVALID_IMAGE, UNSUPPORTED_IMAGE, SAVE_FAILED, OUTSIDE_ROOT, UNREADABLE_FOLDER }
    final Reason reason;
    PhotoFailure(Reason reason) { super(reason.name()); this.reason = reason; }
}
