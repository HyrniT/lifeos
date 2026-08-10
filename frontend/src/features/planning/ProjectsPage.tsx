import { useMemo, useState } from 'react'
import {
  Button,
  DatePicker,
  Dropdown,
  Form,
  Input,
  Modal,
  Progress,
  Segmented,
  Select,
  Tag,
  message,
} from 'antd'
import { Link } from 'react-router-dom'
import dayjs from 'dayjs'
import { FolderKanban, MoreVertical, Plus, Trash2 } from 'lucide-react'
import {
  useCreateProjectMutation,
  useDeleteProjectMutation,
  useProjectsQuery,
  useUpdateProjectMutation,
} from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import {
  DynamicIcon,
  EmptyState,
  PageHeader,
  PanelSkeleton,
  StaggerItem,
  StaggerList,
} from '@/components/ui'
import type { Project, ProjectStatus } from '@/types'

type View = 'active' | 'hold' | 'finished' | 'all'

const STATUS_LABEL: Record<ProjectStatus, string> = {
  ACTIVE: 'active',
  ON_HOLD: 'on hold',
  COMPLETED: 'completed',
  ARCHIVED: 'archived',
}

const STATUS_COLOR: Record<ProjectStatus, string> = {
  ACTIVE: 'default',
  ON_HOLD: 'warning',
  COMPLETED: 'success',
  ARCHIVED: 'default',
}

const ICONS = ['folder', 'folder-kanban', 'rocket', 'home', 'briefcase', 'code', 'palette', 'plane']

export function ProjectsPage() {
  const [view, setView] = useState<View>('active')
  const [editing, setEditing] = useState<Project | null>(null)
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()

  const { data: projects = [], isLoading } = useProjectsQuery()
  const [create, { isLoading: creating }] = useCreateProjectMutation()
  const [update, { isLoading: updating }] = useUpdateProjectMutation()
  const [remove] = useDeleteProjectMutation()

  const visible = useMemo(() => {
    switch (view) {
      case 'active':
        return projects.filter((project) => project.status === 'ACTIVE')
      case 'hold':
        return projects.filter((project) => project.status === 'ON_HOLD')
      case 'finished':
        return projects.filter(
          (project) => project.status === 'COMPLETED' || project.status === 'ARCHIVED',
        )
      default:
        return projects
    }
  }, [projects, view])

  const openComposer = (project: Project | null) => {
    setEditing(project)
    setOpen(true)
  }

  const onSubmit = async (values: Record<string, unknown>) => {
    const body = {
      ...values,
      dueDate: values.dueDate ? (values.dueDate as dayjs.Dayjs).format('YYYY-MM-DD') : null,
    } as Partial<Project>

    try {
      if (editing) {
        await update({ id: editing.id, patch: body }).unwrap()
        message.success('Project updated')
      } else {
        await create(body).unwrap()
        message.success('Project created')
      }
      form.resetFields()
      setOpen(false)
      setEditing(null)
    } catch (err) {
      message.error(errorMessage(err, 'Could not save the project'))
    }
  }

  // PUT replaces the whole project rather than patching it, so a one-field change
  // still has to send the other fields or they are written back as null.
  const setStatus = async (project: Project, status: ProjectStatus) => {
    try {
      await update({
        id: project.id,
        patch: {
          name: project.name,
          description: project.description,
          icon: project.icon,
          color: project.color,
          dueDate: project.dueDate,
          status,
        },
      }).unwrap()
    } catch (err) {
      message.error(errorMessage(err, 'Could not change the status'))
    }
  }

  // The API refuses to delete a project that still has open tasks, and says how
  // many. Surfacing that message is more use than a generic failure toast.
  const confirmDelete = (project: Project) => {
    Modal.confirm({
      title: `Delete "${project.name}"?`,
      content:
        project.taskCount > 0
          ? `${project.taskCount} task(s) belong to this project. Completed ones stay, without a project.`
          : 'This cannot be undone.',
      okText: 'Delete',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await remove(project.id).unwrap()
          message.success('Project deleted')
        } catch (err) {
          message.error(errorMessage(err, 'Could not delete the project'))
        }
      },
    })
  }

  return (
    <>
      <PageHeader
        title="Projects"
        subtitle="Buckets that group tasks and goals. Progress is counted from the tasks inside, not typed in."
        actions={
          <>
            <Segmented
              value={view}
              onChange={(value) => setView(value as View)}
              options={[
                { label: 'Active', value: 'active' },
                { label: 'On hold', value: 'hold' },
                { label: 'Finished', value: 'finished' },
                { label: 'All', value: 'all' },
              ]}
            />
            <Button type="primary" icon={<Plus size={16} />} onClick={() => openComposer(null)}>
              New project
            </Button>
          </>
        }
      />

      {isLoading ? (
        <div className="lo-grid lo-grid--cards">
          {[0, 1, 2].map((index) => (
            <PanelSkeleton key={index} rows={3} />
          ))}
        </div>
      ) : !visible.length ? (
        <EmptyState
          icon={<FolderKanban size={22} />}
          title={projects.length ? 'Nothing in this view' : 'No projects yet'}
          description={
            projects.length
              ? 'No project has that status right now.'
              : 'A project groups the tasks that belong to one piece of work, and shows how far through it you are.'
          }
          action={
            <Button type="primary" icon={<Plus size={15} />} onClick={() => openComposer(null)}>
              {projects.length ? 'New project' : 'Create your first project'}
            </Button>
          }
        />
      ) : (
        <StaggerList>
          <div className="lo-grid lo-grid--cards">
            {visible.map((project) => (
              <StaggerItem key={project.id}>
                <div className="lo-panel" style={{ height: '100%' }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 14 }}>
                    <span
                      style={{
                        display: 'grid',
                        placeItems: 'center',
                        width: 40,
                        height: 40,
                        borderRadius: 12,
                        background: 'var(--surface-container)',
                        color: project.color || undefined,
                        flexShrink: 0,
                      }}
                    >
                      <DynamicIcon name={project.icon} size={18} />
                    </span>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: 15 }}>{project.name}</div>
                      <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                        {/* The task count is the way into the work itself: the task
                            list reads ?project= and filters server-side. */}
                        {project.taskCount ? (
                          // Underlined rather than tinted: in a muted metadata line
                          // a link in the text colour is indistinguishable from the
                          // rest of it, and nobody clicks what does not look
                          // clickable.
                          <Link
                            to={`/planning?project=${project.id}`}
                            title={`Show the tasks in ${project.name}`}
                            style={{
                              color: 'inherit',
                              textDecoration: 'underline',
                              textUnderlineOffset: 3,
                            }}
                          >
                            {project.taskDone}/{project.taskCount} tasks done
                          </Link>
                        ) : (
                          'No tasks yet'
                        )}
                        {project.dueDate && ` · due ${dayjs(project.dueDate).format('D MMM YYYY')}`}
                      </div>
                    </div>
                    <Tag style={{ margin: 0 }} color={STATUS_COLOR[project.status]}>
                      {STATUS_LABEL[project.status]}
                    </Tag>
                    <Dropdown
                      trigger={['click']}
                      menu={{
                        items: [
                          { key: 'edit', label: 'Edit', onClick: () => openComposer(project) },
                          { type: 'divider' },
                          ...(
                            ['ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'] as ProjectStatus[]
                          )
                            .filter((status) => status !== project.status)
                            .map((status) => ({
                              key: status,
                              label: `Mark ${STATUS_LABEL[status]}`,
                              onClick: () => setStatus(project, status),
                            })),
                          { type: 'divider' },
                          {
                            key: 'delete',
                            icon: <Trash2 size={14} />,
                            label: 'Delete',
                            danger: true,
                            onClick: () => confirmDelete(project),
                          },
                        ],
                      }}
                    >
                      <Button type="text" size="small" icon={<MoreVertical size={15} />} />
                    </Dropdown>
                  </div>

                  {project.description && (
                    <p
                      style={{
                        margin: '0 0 12px',
                        fontSize: 13,
                        color: 'var(--on-surface-variant)',
                      }}
                    >
                      {project.description}
                    </p>
                  )}

                  <Progress
                    percent={Math.round(project.progress * 100)}
                    strokeColor="var(--on-surface)"
                    trailColor="var(--outline-variant)"
                  />
                </div>
              </StaggerItem>
            ))}
          </div>
        </StaggerList>
      )}

      <Modal
        open={open}
        onCancel={() => {
          setOpen(false)
          setEditing(null)
        }}
        title={editing ? 'Edit project' : 'New project'}
        okText={editing ? 'Save changes' : 'Create project'}
        confirmLoading={creating || updating}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={onSubmit}
          requiredMark={false}
          // The modal is destroyed on close, so these are read fresh every time it
          // opens — which is what lets one form serve both create and edit. Listed
          // field by field rather than spread: a project carries read-only fields
          // (progress, taskCount) that have no business being posted back.
          initialValues={
            editing
              ? {
                  name: editing.name,
                  description: editing.description,
                  status: editing.status,
                  // A project created through the API may have neither, and
                  // <input type="color"> cannot be handed null.
                  icon: editing.icon || 'folder',
                  color: editing.color || '#7c3aed',
                  dueDate: editing.dueDate ? dayjs(editing.dueDate) : null,
                }
              : { icon: 'folder', color: '#7c3aed', status: 'ACTIVE' }
          }
        >
          <Form.Item
            name="name"
            label="Project"
            rules={[{ required: true, message: 'Name the project' }]}
          >
            <Input placeholder="Move house" autoFocus maxLength={120} />
          </Form.Item>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item name="status" label="Status">
              <Select
                options={(Object.keys(STATUS_LABEL) as ProjectStatus[]).map((value) => ({
                  value,
                  label: STATUS_LABEL[value],
                }))}
              />
            </Form.Item>
            <Form.Item name="dueDate" label="Target date">
              <DatePicker style={{ width: '100%' }} format="D MMM YYYY" />
            </Form.Item>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item name="icon" label="Icon">
              <Select
                options={ICONS.map((icon) => ({
                  value: icon,
                  label: (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                      <DynamicIcon name={icon} size={14} />
                      {icon}
                    </span>
                  ),
                }))}
              />
            </Form.Item>
            <Form.Item name="color" label="Colour">
              <Input type="color" style={{ width: '100%', padding: 2 }} />
            </Form.Item>
          </div>

          <Form.Item name="description" label="What it covers">
            <Input.TextArea rows={2} maxLength={1000} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
