import ManagementPlaceholder from '../components/ManagementPlaceholder'

export default function ReviewPage() {
  return (
    <ManagementPlaceholder
      title="错题复习"
            description="错题记录、复习计划和再练习流程将在答题评分链路完成后接入。"
            items={['错题列表', '复习计划', '再练习入口', '学习记录']}
          />
  )
}
