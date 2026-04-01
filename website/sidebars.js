module.exports = {
  docs: [
    'index',
    'getting-started',
    {
      type: 'category',
      label: 'Configuration',
      items: [
        'configuration/project-structure',
        'configuration/loaders',
        'configuration/metadata',
        'configuration/mappings',
        'configuration/dependencies',
      ],
    },
    'publishing',
    'ci',
    {
      type: 'category',
      label: 'Reference',
      items: [
        'reference/dsl',
        'reference/template-variables',
      ],
    },
    'faq',
  ],
};
