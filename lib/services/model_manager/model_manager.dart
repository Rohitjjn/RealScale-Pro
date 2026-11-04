import 'dart:async';
import 'dart:io';

import 'model_catalog.dart';

/// Device-specific state for one catalog model.
enum ModelInstallStatus {
  bundled,
  notInstalled,
  queued,
  downloading,
  paused,
  installed,
  updateAvailable,
  failed,
  held,
  blocked,
  comingSoon,
}

/// Friendly failure categories that can be localized by the UI.
enum ModelManagerFailure {
  unavailable,
  storageLow,
  downloadFailed,
  checksumFailed,
  installFailed,
  cancelled,
}

/// Current state of one model without exposing downloader-specific objects.
class ModelRuntimeState {
  const ModelRuntimeState({
    required this.model,
    required this.status,
    this.progress = 0,
    this.failure,
    this.installedVersion,
  });

  final ModelDefinition model;
  final ModelInstallStatus status;
  final double progress;
  final ModelManagerFailure? failure;
  final String? installedVersion;

  ModelRuntimeState copyWith({
    ModelInstallStatus? status,
    double? progress,
    ModelManagerFailure? failure,
    bool clearFailure = false,
    String? installedVersion,
    bool clearInstalledVersion = false,
  }) => ModelRuntimeState(
    model: model,
    status: status ?? this.status,
    progress: (progress ?? this.progress).clamp(0.0, 1.0).toDouble(),
    failure: clearFailure ? null : failure ?? this.failure,
    installedVersion: clearInstalledVersion
        ? null
        : installedVersion ?? this.installedVersion,
  );
}

/// Model storage totals for the Model Manager storage bar.
class ModelStorageSummary {
  const ModelStorageSummary({required this.usedBytes, this.freeBytes});

  final int usedBytes;
  final int? freeBytes;
}

/// Download boundary implemented by the background downloader adapter.
abstract interface class ModelTransferGateway {
  Future<File> downloadFile(
    ModelDefinition model,
    ModelFileDefinition file, {
    required void Function(double progress) onProgress,
  });

  Future<void> pauseModel(String modelId);
  Future<void> resumeModel(String modelId);
  Future<void> cancelModel(String modelId);
}

/// Durable model storage boundary, separated for safe unit testing.
abstract interface class ModelInstallStore {
  Future<String?> installedVersion(String modelId);
  Future<void> install(ModelDefinition model, List<File> downloadedFiles);
  Future<void> delete(String modelId);
  Future<ModelStorageSummary> summary();
}

/// Owns install, update, pause/resume and delete state for the whole catalog.
class ModelManager {
  ModelManager({
    required ModelCatalog catalog,
    required ModelTransferGateway transfers,
    required ModelInstallStore store,
    this.selectedModelId,
    this.onSelectedModelDeleted,
  }) : _catalog = catalog,
       _transfers = transfers,
       _store = store;

  final ModelCatalog _catalog;
  final ModelTransferGateway _transfers;
  final ModelInstallStore _store;
  final void Function()? onSelectedModelDeleted;
  final StreamController<List<ModelRuntimeState>> _changes =
      StreamController<List<ModelRuntimeState>>.broadcast();
  final Map<String, ModelRuntimeState> _states = <String, ModelRuntimeState>{};

  String? selectedModelId;

  List<ModelRuntimeState> get states => List<ModelRuntimeState>.unmodifiable(
    _catalog.models.map((ModelDefinition model) => _states[model.id]!),
  );

  Stream<List<ModelRuntimeState>> get changes => _changes.stream;

  Future<void> initialize() async {
    for (final ModelDefinition model in _catalog.models) {
      _states[model.id] = await _initialState(model);
    }
    _emit();
  }

  ModelRuntimeState stateFor(String modelId) {
    final ModelRuntimeState? state = _states[modelId];
    if (state == null) throw StateError('Unknown model id: $modelId');
    return state;
  }

  Future<void> install(String modelId) async {
    final ModelRuntimeState current = stateFor(modelId);
    final ModelDefinition model = current.model;
    if (model.availability != ModelAvailability.onDemand) {
      _set(
        modelId,
        current.copyWith(
          status: ModelInstallStatus.failed,
          failure: ModelManagerFailure.unavailable,
        ),
      );
      return;
    }

    _set(
      modelId,
      current.copyWith(
        status: ModelInstallStatus.queued,
        progress: 0,
        clearFailure: true,
      ),
    );
    final List<File> files = <File>[];
    try {
      for (int index = 0; index < model.files.length; index += 1) {
        final ModelFileDefinition definition = model.files[index];
        final File file = await _transfers.downloadFile(
          model,
          definition,
          onProgress: (double fileProgress) {
            final double overall = (index + fileProgress) / model.files.length;
            _set(
              modelId,
              stateFor(modelId).copyWith(
                status: ModelInstallStatus.downloading,
                progress: overall,
                clearFailure: true,
              ),
            );
          },
        );
        files.add(file);
      }
      await _store.install(model, files);
      _set(
        modelId,
        stateFor(modelId).copyWith(
          status: ModelInstallStatus.installed,
          progress: 1,
          clearFailure: true,
          installedVersion: model.version,
        ),
      );
    } on FileSystemException catch (error) {
      final ModelManagerFailure failure = error.message.contains('checksum')
          ? ModelManagerFailure.checksumFailed
          : ModelManagerFailure.installFailed;
      _set(
        modelId,
        stateFor(
          modelId,
        ).copyWith(status: ModelInstallStatus.failed, failure: failure),
      );
    } on Object {
      _set(
        modelId,
        stateFor(modelId).copyWith(
          status: ModelInstallStatus.failed,
          failure: ModelManagerFailure.downloadFailed,
        ),
      );
    }
  }

  Future<void> pause(String modelId) async {
    final ModelRuntimeState current = stateFor(modelId);
    if (current.status != ModelInstallStatus.downloading) return;
    await _transfers.pauseModel(modelId);
    _set(modelId, current.copyWith(status: ModelInstallStatus.paused));
  }

  Future<void> resume(String modelId) async {
    final ModelRuntimeState current = stateFor(modelId);
    if (current.status != ModelInstallStatus.paused) return;
    await _transfers.resumeModel(modelId);
    _set(modelId, current.copyWith(status: ModelInstallStatus.downloading));
  }

  Future<void> cancel(String modelId) async {
    final ModelRuntimeState current = stateFor(modelId);
    await _transfers.cancelModel(modelId);
    _set(
      modelId,
      current.copyWith(
        status: ModelInstallStatus.notInstalled,
        progress: 0,
        failure: ModelManagerFailure.cancelled,
      ),
    );
  }

  Future<void> delete(String modelId) async {
    final ModelRuntimeState current = stateFor(modelId);
    if (current.model.availability == ModelAvailability.bundled) return;
    await _store.delete(modelId);
    _set(
      modelId,
      current.copyWith(
        status: ModelInstallStatus.notInstalled,
        progress: 0,
        clearFailure: true,
        clearInstalledVersion: true,
      ),
    );
    if (selectedModelId == modelId) {
      selectedModelId = null;
      onSelectedModelDeleted?.call();
    }
  }

  Future<ModelStorageSummary> storageSummary() => _store.summary();

  Future<ModelRuntimeState> _initialState(ModelDefinition model) async {
    switch (model.availability) {
      case ModelAvailability.bundled:
        return ModelRuntimeState(
          model: model,
          status: ModelInstallStatus.bundled,
          progress: 1,
          installedVersion: model.version,
        );
      case ModelAvailability.onDemand:
        final String? installed = await _store.installedVersion(model.id);
        if (installed == null) {
          return ModelRuntimeState(
            model: model,
            status: ModelInstallStatus.notInstalled,
          );
        }
        return ModelRuntimeState(
          model: model,
          status: installed == model.version
              ? ModelInstallStatus.installed
              : ModelInstallStatus.updateAvailable,
          progress: 1,
          installedVersion: installed,
        );
      case ModelAvailability.held:
        return ModelRuntimeState(model: model, status: ModelInstallStatus.held);
      case ModelAvailability.blocked:
        return ModelRuntimeState(
          model: model,
          status: ModelInstallStatus.blocked,
        );
      case ModelAvailability.comingSoon:
        return ModelRuntimeState(
          model: model,
          status: ModelInstallStatus.comingSoon,
        );
    }
  }

  void _set(String id, ModelRuntimeState state) {
    _states[id] = state;
    _emit();
  }

  void _emit() {
    if (!_changes.isClosed) _changes.add(states);
  }

  Future<void> dispose() => _changes.close();
}
