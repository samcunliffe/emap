import git
import shutil

from pathlib import Path
from tqdm import tqdm
from typing import Optional, List

from emap_runner.log import logger
from emap_runner.files import EnvironmentFile
from emap_runner.utils import EMAPRunnerException


class RepoOperationException(EMAPRunnerException):
    """Exception for when a repo cannot be e.g. cloned or updated"""


class _CloneProgressBar(git.RemoteProgress):
    def __init__(self):
        super().__init__()
        self.pbar = tqdm()

    def update(self, op_code, cur_count, max_count=None, message=""):
        self.pbar.total = max_count
        self.pbar.n = cur_count
        self.pbar.refresh()


class Repository:
    def __init__(self, name: str, main_git_url: str, branch: Optional[str] = None):
        """

        :param name:
        :param main_git_url: Main git url for the whole project
        :param branch: Specific git branch e.g. master
        """

        self.name = name
        self.branch = branch
        self._base_git_url = self._base_url_from_https(main_git_url)

    def clone(self, ssh: bool = False) -> None:
        """
        Clone this repo

        :param ssh: Clone the repo using ssh rather than https
        """

        if self.local_version_exists:
            raise EMAPRunnerException(
                f"Cannot clone {self.name} as it " f"already existed"
            )

        logger.info(f"Cloning {self.name:20s} on branch {self.branch:15s}")
        try:
            git.Repo.clone_from(
                url=self.ssh_git_url if ssh else self.https_git_url,
                to_path=self.path,
                branch=self.branch,
                progress=_CloneProgressBar(),
            )

        except git.GitCommandError as e:
            raise RepoOperationException("Repos could not be cloned") from e

        return None

    def update(self) -> None:
        """Update a repo on a specific branch"""
        logger.info(f"Checking out {self}")

        try:

            repo = git.Repo(self.path)
            repo.git.checkout(self.branch)
            repo.remotes[0].pull()

        except git.GitCommandError as e:
            raise RepoOperationException("Cannot checkout branch") from e

        return None

    def clean(self) -> None:
        """Clean this repository from the directory by removing it"""

        if self.local_version_exists:
            shutil.rmtree(self.path)

        else:
            logger.warning(f"Failed to remove {self.name:30s} as it did not exist")

        return None

    @property
    def local_version_exists(self) -> bool:
        return self.path.exists()

    @property
    def path(self) -> Path:
        """Path to this repository"""
        return Path(Path.cwd(), self.name)

    @property
    def https_git_url(self) -> str:
        """Generate a https URL. e.g. https://github.com/../emap-setup.git"""
        return f"https://{self._base_git_url}/{self.name}"

    @property
    def ssh_git_url(self):
        """Generate a valid ssh URL. e.g. git@github.com:.../emap-setup.git"""

        split_url = self._base_git_url.split("/")
        base_url, directory = split_url[0], split_url[1:]

        return f"git@{base_url}:{directory}"

    @property
    def environment_files(self) -> List[EnvironmentFile]:
        """Create an environment file from the example in this repository"""

        if not self.path.exists():
            raise RepoOperationException(
                "Cannot obtain a set of environment "
                f"files as {self.name} did not exist"
            )

        files = []

        for item in self.path.iterdir():

            path = Path(self.path, item)

            if path.is_file() and str(path).endswith("-envs.EXAMPLE"):
                files.append(EnvironmentFile.from_example_file(path))

        return files

    @staticmethod
    def _base_url_from_https(string) -> str:
        """
        Convert either a https link into a base url i.e. strip the https prefix
        """
        https = "https://"

        if https in string:
            return string.split(https)[1]

        else:
            raise ValueError(
                f"Failed to parse {string} as a git URL. "
                f"Expecting it to start with {https}"
            )

    def __str__(self):
        return f"Repository({self.name}, branch={self.branch})"


class Repositories(list):
    @property
    def environment_files(self) -> List[EnvironmentFile]:
        """List of all the environment files associated with all the repos"""
        return sum([repo.environment_files for repo in self], [])

    def update(self) -> None:
        return self._run_for_all("update")

    def clone(self) -> None:
        return self._run_for_all("clone")

    def clean(self) -> None:
        return self._run_for_all("clean")

    def _run_for_all(self, method_name: str) -> None:

        for repo in self:
            getattr(repo, method_name)()

        return None

    @property
    def config_dir_path(self) -> Path:
        """Path of the configuration directory adjacent to all these repos"""
        return Path(Path.cwd(), "config")
