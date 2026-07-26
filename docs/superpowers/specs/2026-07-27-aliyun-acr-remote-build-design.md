# SkillHub 阿里云 ACR 远程构建设计

## 目标

提供一个从开发机发起的构建脚本，通过现有跳板机连接编译服务器，在编译服务器上拉取指定 SkillHub 分支，构建 Linux AMD64 的 server/web 镜像，并推送到阿里云 ACR。

脚本只负责源码同步、镜像构建和推送，不负责部署服务，也不管理 ACR 凭据。

## 使用接口

脚本路径：`docker/docker_build.sh`

```bash
SSH_USER=sam ./docker/docker_build.sh <TAG> [BRANCH]
```

- `TAG` 为必填的镜像标签，只允许 Docker 标签可安全使用的字符。
- `BRANCH` 可选，默认读取开发机当前 Git 分支。
- `SSH_USER` 可选，默认使用 `root`，同时用于跳板机和编译服务器。

示例：

```bash
SSH_USER=sam ./docker/docker_build.sh v0.2.13-auth feature/configurable-auth-entry-policy
```

## 固定配置

- 跳板机：`59.110.17.213`
- 编译服务器：`172.17.20.220`
- Git 仓库：`git@github.com:moonvision-ai/skillhub.git`
- Server 镜像仓库：`registry.cn-beijing.aliyuncs.com/moonvision/skillhub-server`
- Web 镜像仓库：`registry.cn-beijing.aliyuncs.com/moonvision/skillhub-web`
- 构建平台：`linux/amd64`

## 执行流程

1. 开发机校验 `TAG`、`BRANCH`、`git` 和 `ssh`。
2. 显示连接目标、分支和两个完整镜像地址，要求交互确认；设置 `YES=1` 时跳过确认，便于自动化使用。
3. 通过 `ssh -J` 连接编译服务器。
4. 编译服务器首次执行时克隆仓库，后续执行时获取远端更新。
5. 使用远端分支强制重建本地构建分支，确保构建内容精确对应 `origin/<BRANCH>`，不混入编译服务器上的旧改动。
6. 输出实际构建的完整 Git 提交号。
7. 使用 `docker buildx build --platform linux/amd64 --push` 构建并推送 server 镜像。
8. 以相同方式构建并推送 web 镜像。
9. 输出镜像地址、分支、提交号和总耗时。

Server 和 Web 使用同一个 `TAG`，但位于两个独立 ACR 仓库中。

## 失败与安全处理

- 本地和远端均启用严格 Shell 模式，任一步失败时立即停止。
- `TAG`、`BRANCH` 和 `SSH_USER` 在传入远程 Shell 前进行格式校验，避免命令注入。
- 远端源码目录固定为编译用户目录下的 `skillhub`，不删除目录或未跟踪文件。
- 如果远端仓库存在未提交改动，脚本中止并提示人工处理，不覆盖编译服务器上的内容。
- 编译服务器必须预先完成 GitHub SSH 授权、ACR 登录以及 Docker Buildx 配置。
- 脚本不接受、打印或持久化 GitHub/ACR 密码。

## 验证

增加脚本级测试，通过替换 `git`、`ssh` 等外部命令验证：

- 缺少 `TAG` 时拒绝执行。
- 非法 `TAG`、`BRANCH` 或 `SSH_USER` 被拒绝。
- 默认分支正确取自当前 Git 分支。
- `YES=1` 能跳过交互确认。
- 生成的远程构建命令包含正确的仓库、平台、Dockerfile、构建上下文和两条 ACR 镜像地址。
- Shell 语法检查通过。
