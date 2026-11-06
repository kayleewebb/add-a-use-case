// UC-EVA-submit-evaluation extension 4b. The evaluation APIs are stubbed. Login uses the dev stack.

const students = {
  flag: true,
  code: 200,
  message: 'Find students by team id successfully',
  data: [
    { id: 4, firstName: 'John', lastName: 'Smith', username: 'j.smith@abc.edu', email: 'j.smith@abc.edu', enabled: true },
    { id: 5, firstName: 'Eric', lastName: 'Hudson', username: 'e.hudson@abc.edu', email: 'e.hudson@abc.edu', enabled: true },
    { id: 6, firstName: 'Jerry', lastName: 'Moon', username: 'j.moon@abc.edu', email: 'j.moon@abc.edu', enabled: false }
  ]
}

const section = { flag: true, code: 200, message: 'Find section successfully', data: { rubricId: 1 } }

const rubric = {
  flag: true,
  code: 200,
  message: 'Find rubric successfully',
  data: { criteria: [{ criterionId: 1, criterion: 'Quality', description: 'Quality of work', maxScore: 10 }] }
}

const noEvaluationsYet = { flag: true, code: 200, message: 'Find evaluations successfully', data: [] }

function openTheForm() {
  cy.intercept('GET', '**/students/teams/*', { body: students }).as('teammates')
  cy.intercept('GET', '**/sections/*', { body: section })
  cy.intercept('GET', '**/rubrics/*', { body: rubric })
  cy.intercept('GET', '**/evaluations/evaluators/*/week/*', { body: noEvaluationsYet })
  cy.visit('/evaluations/submit-evaluations')
  cy.wait('@teammates')
}

describe('A deactivated teammate is not evaluated', () => {
  beforeEach(() => {
    cy.login('j.smith@abc.edu', '123456')
  })

  it('does not offer a deactivated teammate (step 2)', () => {
    openTheForm()

    cy.get('[data-cy=peer-evaluation-form]')
      .should('contain', 'John Smith')
      .and('contain', 'Eric Hudson')
      .and('not.contain', 'Jerry Moon')
  })

  it('keeps the saves for the active teammates when one request is refused (4b2)', () => {
    cy.intercept('POST', '**/evaluations', (req) => {
      if (req.body.evaluateeId === 5) {
        req.reply({
          statusCode: 400,
          body: { flag: false, code: 400, message: 'A deactivated teammate is not evaluated.' }
        })
      } else {
        req.reply({
          body: {
            flag: true,
            code: 200,
            message: 'Add peer evaluation successfully',
            data: { ...req.body, evaluationId: 40 + req.body.evaluateeId }
          }
        })
      }
    }).as('submit')
    openTheForm()

    cy.get('[data-cy=submit-evaluations]').click()
    cy.get('@submit.all').should('have.length', 2)
    cy.get('[data-cy=peer-evaluation-form]').should('contain', 'John Smith').and('contain', 'Eric Hudson')

    // The saved row is not sent again. Only the refused one is.
    cy.get('[data-cy=submit-evaluations]').click()
    cy.get('@submit.all').should('have.length', 3).then((interceptions) => {
      expect(interceptions[2].request.body.evaluateeId).to.eq(5)
    })
  })
})
