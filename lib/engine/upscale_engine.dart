import '../core/models.dart';

/// Common engine contract. UI code never imports an implementation.
abstract interface class UpscaleEngine {
  String get engineId;
  Stream<EngineEvent> run(JobSpec job);
  Future<void> cancel(String jobId);
  Future<void> dispose();
}

/// One progress or completion message from an engine.
sealed class EngineEvent {
  const EngineEvent();
}

class EngineProgress extends EngineEvent {
  const EngineProgress(this.value);
  final ProgressEvent value;
}

class EngineCompleted extends EngineEvent {
  const EngineCompleted(this.outputPath, this.elapsed);
  final String outputPath;
  final Duration elapsed;
}

class EngineFailed extends EngineEvent {
  const EngineFailed(this.failure);
  final AppFailure failure;
}
