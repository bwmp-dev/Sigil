@Library('bwmp') _

// Sigil is the cross-platform build: Keystone AND Adventure are shaded and
// relocated into the plugin. The jar checks below are what catch a relocation
// regression, which compiles perfectly and only fails at runtime.
mavenPlugin(
    artifacts: 'sigil-plugin/target/Sigil-*.jar,sigil-api/target/sigil-api-*.jar',
    downloads: 'sigil-plugin/target/Sigil-*.jar',
    verify: [
        jar:       'sigil-plugin/target/Sigil-*.jar',
        relocated: ['dev/bwmp/sigil/libs/keystone/', 'dev/bwmp/sigil/libs/kyori/'],
        absent:    ['net/kyori/', 'dev/bwmp/keystone/'],
        present:   ['dev/bwmp/sigil/SigilPlugin.class']
    ]
)
