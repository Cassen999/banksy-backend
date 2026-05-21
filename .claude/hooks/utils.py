import os
import sys


def find_active_feature(repo_root):
    """
    Determine the active feature name by reading plans/.active-feature.
    This file contains the exact name of the plan folder currently being worked on.
    Falls back to most-recently-modified plan folder if marker file is absent,
    and logs a warning so the user knows the fallback is being used.
    """
    marker_path = os.path.join(repo_root, "plans", ".active-feature")

    if os.path.exists(marker_path):
        try:
            with open(marker_path, "r", encoding="utf-8") as f:
                feature_name = f.read().strip()
            if feature_name:
                feature_path = os.path.join(repo_root, "plans", feature_name)
                if os.path.isdir(feature_path):
                    return feature_name
                else:
                    print(
                        f"WARNING: plans/.active-feature contains '{feature_name}' "
                        f"but plans/{feature_name}/ does not exist. "
                        f"Update plans/.active-feature with the correct feature name.",
                        file=sys.stderr
                    )
        except (IOError, OSError):
            pass

    print(
        "WARNING: plans/.active-feature not found. "
        "Falling back to most-recently-modified plan folder. "
        "Create plans/.active-feature containing your current feature name "
        "to ensure the correct feature is always used.",
        file=sys.stderr
    )

    plans_dir = os.path.join(repo_root, "plans")
    if not os.path.exists(plans_dir):
        return None

    candidates = []
    for folder in os.listdir(plans_dir):
        folder_path = os.path.join(plans_dir, folder)
        if not os.path.isdir(folder_path):
            continue
        impl_plan = os.path.join(folder_path, "IMPLEMENTATION_PLAN.md")
        if os.path.exists(impl_plan):
            mtime = os.path.getmtime(folder_path)
            candidates.append((mtime, folder))

    if not candidates:
        return None

    candidates.sort(reverse=True)
    return candidates[0][1]