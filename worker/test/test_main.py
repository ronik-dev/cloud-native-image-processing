import pytest
import os
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient

# Import the app and shared state from main
from main import app, ml_models, safe_path

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def mock_rembg_session(monkeypatch):
    """
    Replace the real rembg session with a mock before every test.
    This prevents the model from being loaded/downloaded during tests.
    The mock's remove() returns fake PNG bytes by default.
    """
    mock_session = MagicMock()
    monkeypatch.setitem(ml_models, "rembg", mock_session)
    return mock_session


@pytest.fixture
def storage_dir(tmp_path, monkeypatch):
    """
    Point STORAGE_DATA_DIR at a temporary directory for each test.
    Files written during tests are isolated and cleaned up automatically.
    """
    monkeypatch.setenv("STORAGE_DATA_DIR", str(tmp_path))
    return tmp_path


@pytest.fixture
def client():
    """
    FastAPI TestClient — calls endpoints without running a real server.
    httpx is used under the hood; no network traffic is involved.
    """
    return TestClient(app)


@pytest.fixture
def source_image(storage_dir):
    """
    Write a minimal fake image file to the temp storage directory.
    Returns the full path so tests can reference it as a storage key.
    """
    path = storage_dir / "source-uuid"
    path.write_bytes(b"fake-image-bytes")
    return str(path)


@pytest.fixture
def target_path(storage_dir):
    """
    Return a path inside the temp storage directory for output files.
    The file does not exist yet — endpoints are expected to create it.
    """
    return str(storage_dir / "target-uuid")


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

class TestHealth:

    def test_returns_200(self, client):
        response = client.get("/health")
        assert response.status_code == 200

    def test_returns_ok_status(self, client):
        response = client.get("/health")
        assert response.json()["status"] == "ok"

    def test_lists_loaded_models(self, client):
        response = client.get("/health")
        assert "rembg" in response.json()["models"]


# ---------------------------------------------------------------------------
# safe_path
# ---------------------------------------------------------------------------

class TestSafePath:

    def test_valid_key_resolves_inside_storage_dir(self, storage_dir, monkeypatch):
        monkeypatch.setenv("STORAGE_DATA_DIR", str(storage_dir))
        result = safe_path("some-uuid")
        assert result.startswith(str(storage_dir))

    def test_path_traversal_raises_value_error(self, storage_dir, monkeypatch):
        monkeypatch.setenv("STORAGE_DATA_DIR", str(storage_dir))
        with pytest.raises(ValueError):
            safe_path("../../etc/passwd")

    def test_nested_traversal_raises_value_error(self, storage_dir, monkeypatch):
        monkeypatch.setenv("STORAGE_DATA_DIR", str(storage_dir))
        with pytest.raises(ValueError):
            safe_path("valid/../../../etc/shadow")


# ---------------------------------------------------------------------------
# POST /convert_format
# ---------------------------------------------------------------------------

class TestConvertFormat:

    def test_returns_200_and_target_sk_on_success(self, client, source_image, target_path):
        with patch("main.ffmpeg") as mock_ffmpeg:
            mock_ffmpeg.input.return_value.output.return_value.run.return_value = None

            response = client.post("/convert_format", json={
                "source_sk": source_image,
                "input_format": "png",
                "target_sk": target_path,
                "output_format": "jpg"
            })

        assert response.status_code == 200
        assert response.json()["target_sk"] == target_path

    def test_calls_ffmpeg_with_correct_formats(self, client, source_image, target_path):
        with patch("main.ffmpeg") as mock_ffmpeg:
            mock_input = MagicMock()
            mock_output = MagicMock()
            mock_ffmpeg.input.return_value = mock_input
            mock_input.output.return_value = mock_output
            mock_output.run.return_value = None

            client.post("/convert_format", json={
                "source_sk": source_image,
                "input_format": "png",
                "target_sk": target_path,
                "output_format": "jpg"
            })

            mock_ffmpeg.input.assert_called_once_with(source_image, format="png")
            mock_input.output.assert_called_once_with(target_path, format="jpg")

    def test_returns_500_when_ffmpeg_raises(self, client, source_image, target_path):
        with patch("main.ffmpeg") as mock_ffmpeg:
            mock_error = MagicMock()
            mock_error.stderr = b"ffmpeg: invalid data"
            mock_ffmpeg.Error = Exception
            mock_ffmpeg.input.return_value.output.return_value.run.side_effect = Exception("ffmpeg: invalid data")

            response = client.post("/convert_format", json={
                "source_sk": source_image,
                "input_format": "png",
                "target_sk": target_path,
                "output_format": "jpg"
            })

        assert response.status_code == 500

    def test_returns_422_when_body_is_missing_fields(self, client):
        response = client.post("/convert_format", json={
            "source_sk": "some-key"
            # missing input_format, target_sk, output_format
        })
        assert response.status_code == 422

    def test_returns_422_when_body_is_empty(self, client):
        response = client.post("/convert_format", json={})
        assert response.status_code == 422

    def test_returns_422_when_content_type_is_wrong(self, client):
        response = client.post("/convert_format", data="not-json")
        assert response.status_code == 422


# ---------------------------------------------------------------------------
# POST /remove_background
# ---------------------------------------------------------------------------

class TestRemoveBackground:

    def test_returns_200_and_target_sk_on_success(
            self, client, source_image, target_path, mock_rembg_session):

        with patch("main.remove") as mock_remove:
            mock_remove.return_value = b"fake-output-image-bytes"

            response = client.post("/remove_background", json={
                "source_sk": source_image,
                "target_sk": target_path
            })

        assert response.status_code == 200
        assert response.json()["target_sk"] == target_path

    def test_output_file_is_written_to_disk(
            self, client, source_image, target_path, mock_rembg_session):

        fake_output = b"processed-image-bytes"
        with patch("main.remove") as mock_remove:
            mock_remove.return_value = fake_output

            client.post("/remove_background", json={
                "source_sk": source_image,
                "target_sk": target_path
            })

        assert os.path.exists(target_path)
        with open(target_path, "rb") as f:
            assert f.read() == fake_output

    def test_calls_remove_with_correct_session(
            self, client, source_image, target_path, mock_rembg_session):

        with patch("main.remove") as mock_remove:
            mock_remove.return_value = b"output"

            client.post("/remove_background", json={
                "source_sk": source_image,
                "target_sk": target_path
            })

            mock_remove.assert_called_once_with(b"fake-image-bytes", session=mock_rembg_session)

    def test_returns_500_when_source_file_not_found(self, client, target_path):
        response = client.post("/remove_background", json={
            "source_sk": "/nonexistent/path/uuid",
            "target_sk": target_path
        })
        assert response.status_code == 500

    def test_returns_500_when_rembg_raises(
            self, client, source_image, target_path, mock_rembg_session):

        with patch("main.remove") as mock_remove:
            mock_remove.side_effect = RuntimeError("model inference failed")

            response = client.post("/remove_background", json={
                "source_sk": source_image,
                "target_sk": target_path
            })

        assert response.status_code == 500

    def test_returns_422_when_body_is_missing_fields(self, client):
        response = client.post("/remove_background", json={
            "source_sk": "some-key"
            # missing target_sk
        })
        assert response.status_code == 422

    def test_returns_422_when_body_is_empty(self, client):
        response = client.post("/remove_background", json={})
        assert response.status_code == 422
